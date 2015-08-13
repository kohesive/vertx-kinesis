package org.collokia.vertx.kinesis.impl

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.kinesis.AmazonKinesisAsync
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient
import com.amazonaws.services.kinesis.model.CreateStreamRequest
import com.amazonaws.services.kinesis.model.DescribeStreamRequest
import com.amazonaws.services.kinesis.model.GetShardIteratorRequest
import com.amazonaws.services.kinesis.model.PutRecordRequest
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import org.collokia.vertx.kinesis.KinesisClient
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.Delegates

class KinesisClientImpl(val vertx: Vertx, val config: JsonObject) : KinesisClient {

    companion object {
        private val log = LoggerFactory.getLogger(javaClass)
    }

    var client: AmazonKinesisAsync by Delegates.notNull()

    private var initialized = AtomicBoolean(false)

    override fun createStream(streamName: String, shardCount: Int?, resultHandler: Handler<AsyncResult<Void?>>) {
        withClient { client ->
            client.createStreamAsync(CreateStreamRequest().withStreamName(streamName).withShardCount(shardCount), resultHandler.toKinesisHandler())
        }
    }

    override fun describeStream(streamName: String, limit: Int?, exclusiveStartShardId: String?, resultHandler: Handler<AsyncResult<JsonObject>>) {
        withClient { client ->
            client.describeStreamAsync(
                DescribeStreamRequest().withStreamName(streamName).withLimit(limit).withExclusiveStartShardId(exclusiveStartShardId),
                resultHandler.withConverter {
                    with(it.getStreamDescription()) {
                        JsonObject()
                            .put("streamName", this.getStreamName())
                            .put("streamARN", this.getStreamARN())
                            .put("streamStatus", this.getStreamStatus())
                            .put("hasMoreShards", this.isHasMoreShards())
                            .put("shards", JsonArray(this.getShards().map { shard ->
                                JsonObject()
                                    .put("shardId", shard.getShardId())
                                    .put("parentShardId", shard.getParentShardId())
                                    .put("adjacentParentShardId", shard.getAdjacentParentShardId())
                                    .put("hashKeyRange", shard?.getHashKeyRange().let { hashRange ->
                                        JsonObject()
                                            .put("startingHashKey", hashRange?.getStartingHashKey())
                                            .put("endingHashKey", hashRange?.getEndingHashKey())
                                    })
                                    .put("sequenceNumberRange", shard?.getSequenceNumberRange().let { sequenceRange ->
                                        JsonObject()
                                            .put("startingSequenceNumber", sequenceRange?.getStartingSequenceNumber())
                                            .put("endingSequenceNumber", sequenceRange?.getEndingSequenceNumber())
                                    })
                            })
                        )
                    }
                }
            )
        }
    }

    override fun putRecord(streamName: String, record: JsonObject, resultHandler: Handler<AsyncResult<JsonObject>>) {
        withClient { client ->
            client.putRecordAsync(PutRecordRequest()
                .withData(ByteBuffer.wrap(record.getBinary("data")))
                .withExplicitHashKey(record.getString("explicitHashKey"))
                .withPartitionKey(record.getString("partitionKey"))
                .withStreamName(streamName), resultHandler.withConverter {
                    JsonObject()
                        .put("shardId", it.getShardId())
                        .put("sequenceNumber", it.getSequenceNumber())
                }
            )
        }
    }

    override fun getShardIterator(streamName: String, shardId: String, shardIteratorType: String, startingSequenceNumber: String?, resultHandler: Handler<AsyncResult<String>>) {
        withClient { client ->
            client.getShardIteratorAsync(GetShardIteratorRequest()
                .withShardId(shardId)
                .withShardIteratorType(shardIteratorType)
                .withStartingSequenceNumber(startingSequenceNumber), resultHandler.withConverter { it.getShardIterator() }
            )
        }
    }

    override fun start(resultHandler: Handler<AsyncResult<Void>>) {
        log.info("Starting Kinesis client");

        vertx.executeBlocking(Handler { future ->
            try {
                val credentials: AWSCredentials = if (config.getString("accessKey") != null) {
                    BasicAWSCredentials(config.getString("accessKey"), config.getString("secretKey"))
                } else {
                    try {
                        ProfileCredentialsProvider().getCredentials()
                    } catch (t: Throwable) {
                        throw AmazonClientException(
                            "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format."
                        )
                    }
                }

                client = AmazonKinesisAsyncClient(credentials)

                val region = config.getString("region")
                client.setRegion(Region.getRegion(Regions.fromName(region)))
                if (config.getString("host") != null && config.getInteger("port") != null) {
                    client.setEndpoint("http://${ config.getString("host") }:${ config.getInteger("port") }")
                }

                initialized.set(true)

                future.complete()
            } catch (t: Throwable) {
                future.fail(t)
            }
        }, true, resultHandler)
    }

    override fun stop(resultHandler: Handler<AsyncResult<Void>>) {
        resultHandler.handle(Future.succeededFuture()) // nothing
    }

    private fun withClient(handler: (AmazonKinesisAsync) -> Unit) {
        if (initialized.get()) {
            handler(client)
        } else {
            throw IllegalStateException("Kinesis client wasn't initialized")
        }
    }

    fun <KinesisRequest : AmazonWebServiceRequest> Handler<AsyncResult<Void?>>.toKinesisHandler(): AsyncHandler<KinesisRequest, Void?> = withConverter { it }

    fun <KinesisRequest : AmazonWebServiceRequest, KinesisResult, VertxResult> Handler<AsyncResult<VertxResult>>.withConverter(
            converter: (KinesisResult) -> VertxResult
    ): KinesisToVertxHandlerAdapter<KinesisRequest, KinesisResult, VertxResult> =
        KinesisToVertxHandlerAdapter(
            vertxHandler                = this,
            kinesisResultToVertxMapper  = converter
        )

    class KinesisToVertxHandlerAdapter<KinesisRequest : AmazonWebServiceRequest, KinesisResult, VertxResult>(
        val vertxHandler: Handler<AsyncResult<VertxResult>>,
        val kinesisResultToVertxMapper: (KinesisResult) -> VertxResult
    ) : AsyncHandler<KinesisRequest, KinesisResult> {

        override fun onSuccess(request: KinesisRequest, result: KinesisResult) {
            vertxHandler.handle(Future.succeededFuture(kinesisResultToVertxMapper(result)))
        }

        override fun onError(exception: Exception) {
            vertxHandler.handle(Future.failedFuture(exception))
        }
    }
    
}