package org.collokia.vertx.kinesis

import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.logging.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.Delegates

class KinesisShardConsumerVerticle : KinesisVerticle() {

    companion object {
        private val log = LoggerFactory.getLogger("KinesisShardConsumerVerticle")
    }

    private var timerId: Long by Delegates.notNull()
    private var shardIterator: String by Delegates.notNull()
    private var shouldStop = AtomicBoolean(false)

    override fun startAfterClientInit(startFuture: Future<Void>) {
        val initialShardIterator = config().getString("shardIterator")

        fun scheduleGetRecords() {
            timerId = vertx.setPeriodic(200) { // TODO: configure
                routeRecords()
            }
        }

        if (initialShardIterator != null) {
            shardIterator = initialShardIterator
            scheduleGetRecords()
            startFuture.complete()
        } else {
            vertxClient.getShardIterator(getStreamName(), getShardId(), "TRIM_HORIZON", null, Handler {
                if (it.succeeded()) {
                    shardIterator = it.result()
                    scheduleGetRecords()
                    startFuture.complete()
                } else {
                    startFuture.fail(it.cause())
                }
            })
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

                val nextShardIterator = result.getString("nextShardIterator")
                val jsonArray = result.getJsonArray("records")
                println("Got records: $jsonArray") // TODO: delete this

                val deliveryOptions = DeliveryOptions().addHeader("nextShardIterator", nextShardIterator)
                jsonArray.forEach { recordJson ->
                    vertx.eventBus().send(address, recordJson)
                }

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

    override fun stopBeforeClientDispose() {
        vertx.cancelTimer(timerId)
    }

    private fun getShardId() = config().getString("shardId")

}