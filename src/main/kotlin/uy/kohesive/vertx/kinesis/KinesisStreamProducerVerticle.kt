package uy.kohesive.vertx.kinesis

import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import kotlin.properties.Delegates

class KinesisStreamProducerVerticle : KinesisVerticle() {

    companion object {
        private val log = LoggerFactory.getLogger("KinesisStreamProducerVerticle")
    }

    var consumer: MessageConsumer<JsonObject> by Delegates.notNull()

    override fun startAfterClientInit(startFuture: Future<Void>) {
        consumer = vertx.eventBus().consumer(getAddress(), Handler { message: Message<JsonObject> ->
            vertxClient.putRecord(getStreamName(), message.body()) {
                if (it.failed()) {
                    log.error("Can't put record to stream ${ getStreamName() }", it.cause())
                }
            }
        })
        consumer.completionHandler {
            if (it.succeeded()) {
                startFuture.complete()
            } else {
                startFuture.fail(it.cause())
            }
        }
    }

    override fun stopAfterClientDispose(stopFuture: Future<Void>) {
        consumer.unregister() {
            if (it.succeeded()) {
                stopFuture.complete()
            } else {
                stopFuture.fail(it.cause())
            }
        }
    }

    private fun getAddress() = config().getString("address")


}