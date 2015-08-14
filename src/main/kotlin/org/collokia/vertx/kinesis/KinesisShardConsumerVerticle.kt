package org.collokia.vertx.kinesis

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.logging.LoggerFactory
import org.collokia.vertx.kinesis.impl.KinesisClientImpl
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.Delegates

class KinesisShardConsumerVerticle : AbstractVerticle() {

    companion object {
        private val log = LoggerFactory.getLogger("KinesisShardConsumerVerticle")
    }

    private var vertxClient: KinesisClient by Delegates.notNull()
    private var timerId: Long by Delegates.notNull()
    private var shardIterator: String by Delegates.notNull()

    private var shouldStop = AtomicBoolean(false)

    override fun start(startFuture: Future<Void>) {
        val streamName = getStreamName()
        val shardId    = getShardId()

        log.info("Starting consuming shard $shardId of stream $streamName")

        vertxClient = KinesisClientImpl(vertx, config())
        vertxClient.start {
            if (it.succeeded()) {                                 // TODO: ???
                vertxClient.getShardIterator(streamName, shardId, "TRIM_HORIZON", null, Handler {
                    if (it.succeeded()) {
                        shardIterator = it.result()

                        timerId = vertx.setPeriodic(200) { // TODO: configure
                            routeRecords()
                        }

                        startFuture.complete()
                    } else {
                        startFuture.fail(it.cause())
                    }
                })
            } else {
                startFuture.fail(it.cause())
            }
        }
    }

    private fun routeRecords() {
        if (shouldStop.get()) {
            return
        }

        val streamName = getStreamName()
        val address    = config().getString("address")

        vertxClient.getRecords(shardIterator, null, Handler {
            if (it.succeeded()) {
                val result = it.result()

                val jsonArray = result.getJsonArray("records")
                println("Got records: $jsonArray") // TODO: delete this
                jsonArray.forEach { recordJson ->
                    vertx.eventBus().send(address, recordJson)
                }

                val nextShardIterator = result.getString("nextShardIterator")
                if (nextShardIterator != null) {
                    shardIterator = nextShardIterator
                } else {
                    shouldStop.set(true)
                    log.info("No more records would be available from the iterator for shard ${ getShardId() }")
                }
            } else {
                log.error("Unable to get records from shard $streamName of stream $streamName", it.cause())
            }
        })
    }

    private fun getStreamName() = config().getString("streamName")
    private fun getShardId() = config().getString("shardId")

    override fun stop(stopFuture: Future<Void>) {
        // Undeploy client
        vertx.cancelTimer(timerId)
        vertxClient.stop {
            if (it.succeeded()) {
                stopFuture.complete()
            } else {
                stopFuture.fail(it.cause())
            }
        }
    }
}