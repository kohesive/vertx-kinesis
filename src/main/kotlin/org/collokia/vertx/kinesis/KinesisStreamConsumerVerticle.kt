package org.collokia.vertx.kinesis

import com.amazonaws.services.kinesis.AmazonKinesisAsync
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.logging.LoggerFactory
import org.collokia.vertx.kinesis.impl.KinesisClientImpl
import kotlin.properties.Delegates

class KinesisStreamConsumerVerticle : AbstractVerticle() {

    companion object {
        private val log = LoggerFactory.getLogger("KinesisStreamConsumerVerticle")
    }

    private var client: AmazonKinesisAsync by Delegates.notNull()
    private var vertxClient: KinesisClient by Delegates.notNull()

    override fun start(startFuture: Future<Void>) {
        vertxClient = KinesisClientImpl(vertx, config())
        vertxClient.start {
            if (it.succeeded()) {
                client = (vertxClient as KinesisClientImpl).client

                // TODO: start consuming

                startFuture.complete()
            } else {
                startFuture.fail(it.cause())
            }
        }
    }

    override fun stop(stopFuture: Future<Void>) {
        vertxClient.stop {
            if (it.succeeded()) {
                stopFuture.complete()
            } else {
                stopFuture.fail(it.cause())
            }
        }
    }
}