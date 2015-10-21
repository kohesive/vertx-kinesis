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
import com.amazonaws.services.kinesis.model.*
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import org.collokia.vertx.kinesis.KinesisClient
import org.collokia.vertx.kinesis.util.toByteArray
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.Delegates

class KinesisClientImpl(val vertx: Vertx, val config: JsonObject) : KinesisClient {

    companion object {
        private val log = LoggerFactory.getLogger(KinesisClientImpl::class.java)
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
                    with(it.streamDescription) {
                        JsonObject()
                            .put("streamName", this.streamName)
                            .put("streamARN", this.streamARN)
                            .put("streamStatus", this.streamStatus)
                            .put("hasMoreShards", this.isHasMoreShards)
                            .put("shards", JsonArray(this.shards.map { shard ->
                                JsonObject()
                                    .put("shardId", shard.shardId)
                                    .put("parentShardId", shard.parentShardId)
                                    .put("adjacentParentShardId", shard.adjacentParentShardId)
                                    .put("hashKeyRange", shard?.hashKeyRange.let { hashRange ->
                                        JsonObject()
                                            .put("startingHashKey", hashRange?.startingHashKey)
                                            .put("endingHashKey", hashRange?.endingHashKey)
                                    })
                                    .put("sequenceNumberRange", shard?.sequenceNumberRange.let { sequenceRange ->
                                        JsonObject()
                                            .put("startingSequenceNumber", sequenceRange?.startingSequenceNumber)
                                            .put("endingSequenceNumber", sequenceRange?.endingSequenceNumber)
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
                        .put("shardId", it.shardId)
                        .put("sequenceNumber", it.sequenceNumber)
                }
            )
        }
    }

    override fun getShardIterator(streamName: String, shardId: String, shardIteratorType: String, startingSequenceNumber: String?, resultHandler: Handler<AsyncResult<String>>) {
        withClient { client ->
            client.getShardIteratorAsync(GetShardIteratorRequest()
                .withStreamName(streamName)
                .withShardId(shardId)
                .withShardIteratorType(shardIteratorType)
                .withStartingSequenceNumber(startingSequenceNumber), resultHandler.withConverter { it.shardIterator }
            )
        }
    }

    override fun getRecords(shardIterator: String, limit: Int?, resultHandler: Handler<AsyncResult<JsonObject>>) {
        withClient { client ->
            client.getRecordsAsync(GetRecordsRequest().withShardIterator(shardIterator).withLimit(limit), resultHandler.withConverter {
                JsonObject()
                    .put("nextShardIterator", it.nextShardIterator)
                    .put("millisBehindLatest", it.millisBehindLatest)
                    .put("records", JsonArray(it.records.map { record ->
                        JsonObject()
                            .put("sequenceNumber", record.sequenceNumber)
                            .put("data", record.data.toByteArray())
                            .put("partitionKey", record.partitionKey)
                    }))
            })
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
                        ProfileCredentialsProvider().credentials
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