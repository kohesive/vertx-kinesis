package org.collokia.vertx.kinesis

import org.collokia.vertx.util.putToSharedMemoryAsync
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.Delegates

abstract class AbstractKinesisShardConsumerVerticle : KinesisVerticle() {

    companion object {
        private val log = LoggerFactory.getLogger("KinesisShardConsumerVerticle")
        private val DefaultPollingInterval: Long = 500
    }

    private var timerId: Long by Delegates.notNull()
    private var shardIterator: String by Delegates.notNull()
    private var shouldStop = AtomicBoolean(false)

    override fun startAfterClientInit(startFuture: Future<Void>) {
        val initialShardIterator = config().getString("shardIterator")

        fun scheduleGetRecords() {
            timerId = vertx.setPeriodic(config().getLong("pollingInterval") ?: DefaultPollingInterval) {
                routeRecords()
            }
        }

        if (initialShardIterator != null) {
            shardIterator = initialShardIterator
            log.info("Starting shard consumer with iterator = $shardIterator")
            scheduleGetRecords()
            startFuture.complete()
        } else {
            log.info("Starting shard consumer without initial iterator")
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

    protected abstract fun processRecords(records: List<JsonObject>)

    private fun routeRecords() {
        if (shouldStop.get()) {
            return
        }

        vertxClient.getRecords(shardIterator, null, Handler {
            if (it.succeeded()) {
                val result = it.result()

                processRecords(result.getJsonArray("records").map { it as? JsonObject }.filterNotNull())

                val nextShardIterator = result.getString("nextShardIterator")
                if (nextShardIterator != null) {
                    shardIterator = nextShardIterator
                    vertx.putToSharedMemoryAsync(KinesisVerticle.ShardIteratorMapName, getShardIteratorKey(getShardId()), nextShardIterator, Handler {
                        if (!it.succeeded()) {
                            log.error("Unable to store next shard iterator to shared memory", it.cause())
                        }
                    })
                } else {
                    shouldStop.set(true)
                    log.info("No more records would be available from the iterator for shard ${ getShardId() }")
                }
            } else {
                log.error("Unable to get records from shard ${ getShardId() } of stream ${ getStreamName() }", it.cause())
            }
        })
    }

    override fun stopBeforeClientDispose() {
        vertx.cancelTimer(timerId)
    }

    private fun getShardId() = config().getString("shardId")

}