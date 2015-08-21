package org.collokia.vertx.kinesis

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import org.collokia.vertx.kinesis.impl.KinesisClientImpl
import kotlin.properties.Delegates

abstract class KinesisVerticle : AbstractVerticle() {

    companion object {
        val ShardIteratorMapName = "kinesis-sharditerator"
    }

    protected var vertxClient: KinesisClient by Delegates.notNull()

    protected fun getStreamName(): String = config().getString("streamName")

    protected fun getShardIteratorKey(shardId: String): String = "${ getStreamName() }-$shardId"

    final override fun start(startFuture: Future<Void>) {
        startBeforeClientInit()

        vertxClient = KinesisClientImpl(vertx, config())
        vertxClient.start {
            if (it.succeeded()) {
                startAfterClientInit(startFuture)
            } else {
                startFuture.fail(it.cause())
            }
        }
    }

    final override fun stop(stopFuture: Future<Void>) {
        stopBeforeClientDispose()

        vertxClient.stop {
            if (it.succeeded()) {
                stopAfterClientDispose(stopFuture)
            } else {
                stopFuture.fail(it.cause())
            }
        }
    }

    open protected fun startAfterClientInit(startFuture: Future<Void>) { startFuture.complete() }

    open protected fun stopAfterClientDispose(stopFuture: Future<Void>) { stopFuture.complete() }

    open protected fun startBeforeClientInit() {}

    open protected fun stopBeforeClientDispose() {}

}