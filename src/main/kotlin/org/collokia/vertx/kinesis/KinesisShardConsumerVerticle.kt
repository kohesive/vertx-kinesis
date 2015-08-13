package org.collokia.vertx.kinesis

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.logging.LoggerFactory
import org.collokia.vertx.kinesis.impl.KinesisClientImpl
import kotlin.properties.Delegates

class KinesisShardConsumerVerticle : AbstractVerticle() {

    companion object {
        private val log = LoggerFactory.getLogger("KinesisShardConsumerVerticle")
    }

    private var vertxClient: KinesisClient by Delegates.notNull()

    private var shardIterator: String by Delegates.notNull()

    override fun start(startFuture: Future<Void>) {
        val streamName = config().getString("streamName")
        val shardId    = config().getString("shardId")

        vertxClient = KinesisClientImpl(vertx, config())
        vertxClient.start {
            if (it.succeeded()) {
                vertxClient.getShardIterator(streamName, shardId, "TRIM_HORIZON", null, Handler {
                    if (it.succeeded()) {
                        shardIterator = it.result()

                        // TODO: schedule routeRecords()

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
        val streamName = config().getString("streamName")
        val shardId    = config().getString("shardId")
        val address    = config().getString("address")

        vertxClient.getRecords(shardIterator, null, Handler {
            if (it.succeeded()) {
                val result = it.result()

                result.getJsonArray("records").forEach { recordJson ->
                    vertx.eventBus().send(address, recordJson)
                }

                shardIterator = result.getString("nextShardIterator")

                // TODO: next routeRecords() call ?
            } else {
                log.error("Unable to get records from shard $streamName of stream $streamName", it.cause())
            }
        })
    }

    override fun stop(stopFuture: Future<Void>) {
        // Undeploy client
        vertxClient.stop {
            if (it.succeeded()) {
                stopFuture.complete()
            } else {
                stopFuture.fail(it.cause())
            }
        }
    }
}