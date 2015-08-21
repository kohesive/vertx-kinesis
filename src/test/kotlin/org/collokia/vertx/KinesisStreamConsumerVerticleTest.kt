package org.collokia.vertx

import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.collokia.vertx.kinesis.KinesisClient
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.platform.platformStatic
import kotlin.properties.Delegates
import kotlin.test.assertEquals

@RunWith(VertxUnitRunner::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class KinesisStreamConsumerVerticleTest {

    companion object {
        val vertx: Vertx = Vertx.vertx()

        val KinesalitePort = 4567
        val KinesaliteHost = "localhost"

        val StreamName = "TestStream"
        val Address = "kinesis.stream.test"

        var client: KinesisClient by Delegates.notNull()

        val config = JsonObject()
            .put("region", "us-west-2")
            .put("streamName", StreamName)
            .put("address", Address)
            .put("host", KinesaliteHost)
            .put("port", KinesalitePort)
            .put("shardConsumerVerticleName", "org.collokia.vertx.kinesis.KinesisMessageBusShardConsumerVerticle")

        @BeforeClass
        @platformStatic
        fun before(context: TestContext) {
            client = KinesisClient.create(vertx, config)
            val latch = CountDownLatch(1)
            client.start(context.asyncAssertSuccess { latch.countDown() })
            latch.await(10, TimeUnit.SECONDS)
        }

        @AfterClass
        @platformStatic
        fun after(context: TestContext) {
            client.stop(context.asyncAssertSuccess())
            vertx.close(context.asyncAssertSuccess())
        }
    }

    @Test
    fun testConsume(context: TestContext) {
        client.createStream(StreamName, 2, context.asyncAssertSuccess() {
            // Stream must be created by now
            // Wait for it to become active
            val counter = AtomicInteger(5)
            val streamActive = AtomicBoolean(false)

            while (counter.getAndDecrement() > 0 && !streamActive.get()) {
                val latch = CountDownLatch(1)


                client.describeStream(StreamName, null, null, context.asyncAssertSuccess() { describeJson ->
                    if (describeJson.getString("streamStatus") == "ACTIVE") {
                        streamActive.set(true)
                    }

                    latch.countDown()
                })

                latch.await(3, TimeUnit.SECONDS)
                Thread.sleep(500)
            }

            context.assertTrue(streamActive.get())

            // Now the stream is active
            // Deploy the producer verticle and start listening to the address
            vertx.deployVerticle("org.collokia.vertx.kinesis.KinesisStreamConsumerVerticle", DeploymentOptions().setConfig(config), context.asyncAssertSuccess() {
                vertx.executeBlocking(Handler { future: Future<Void> ->
                    val receiveLatch = CountDownLatch(1)

                    // Verticle id deployed, let's start consuming messages from the address configured
                    vertx.eventBus().consumer(Address, Handler { message: Message<JsonObject> ->
                        println("Got the message in the message bus: ${ message.body() }")
                        assertEquals("Hello World", String(message.body().getBinary("data"), "UTF-8"))

                        receiveLatch.countDown()
                    })

                    val record = JsonObject()
                        .put("data", "Hello World".getBytes("UTF-8"))
                        .put("partitionKey", "p1")

                    client.putRecord(StreamName, record, context.asyncAssertSuccess())

                    if (receiveLatch.await(10, TimeUnit.SECONDS)) {
                        future.complete()
                    } else {
                        future.fail("Didn't receive the right message in time")
                    }
                }, context.asyncAssertSuccess())
            })
        })
    }

}