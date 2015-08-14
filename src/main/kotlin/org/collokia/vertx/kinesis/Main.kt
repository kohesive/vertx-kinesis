package org.collokia.vertx.kinesis

import io.vertx.core.DeploymentOptions
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val vertx  = Vertx.vertx()

    val streamName = "TestStream"
    val address    = "kinesis.TestStream"

    val config = JsonObject()
        .put("region", "us-west-2")
        .put("streamName", streamName)
        .put("address", address)

    vertx.eventBus().consumer(address) { message: Message<JsonObject> ->
        val messageReceived = String(message.body().getBinary("data"), "UTF-8")
        println("Received message: $messageReceived")
    }

    vertx.deployVerticle("org.collokia.vertx.kinesis.KinesisStreamConsumerVerticle", DeploymentOptions().setConfig(config), Handler {
        if (!it.succeeded()) {
            it.cause().printStackTrace()
        }
    })

    Thread.sleep(100000)
}

fun main1(args: Array<String>) {
    val vertx  = Vertx.vertx()

    val streamName = "TestStream"
    val address    = "kinesis.TestStream"

    val config = JsonObject()
        .put("region", "us-west-2")
        .put("streamName", streamName)
        .put("address", address)

    val messages = (1..5).map { "Message #$it".toByteArray("UTF-8") }

    val kinesisClient = KinesisClient.create(vertx, config)
    kinesisClient.start(Handler {
        if (it.succeeded()) {
            val latch = CountDownLatch(messages.size())

            messages.forEach { message ->
                // TODO:
                val record = JsonObject()
                    .put("data", message)
                    .put("partitionKey", "someKey")

                kinesisClient.putRecord(streamName, record, Handler {
                    if (it.succeeded()) {
                        println("Published record ${ String(message, "UTF-8") }")
                        latch.countDown()
                    } else {
                        it.cause().printStackTrace()
                    }
                })
            }

            latch.await(5, TimeUnit.SECONDS)
        } else {
            it.cause().printStackTrace()
        }
    })

    Thread.sleep(100000)
}