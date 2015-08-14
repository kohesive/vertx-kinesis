package org.collokia.vertx.kinesis

import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import org.collokia.vertx.kinesis.impl.KinesisClientImpl
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

class KinesisStreamConsumerVerticle : AbstractVerticle() {

    companion object {
        private val log = LoggerFactory.getLogger("KinesisStreamConsumerVerticle")
        private val ShardConsumerVerticleName = "org.collokia.vertx.kinesis.KinesisShardConsumerVerticle"
    }

    private var vertxClient: KinesisClient by Delegates.notNull()

    private var shardVerticlesDeploymentIds = CopyOnWriteArrayList<String>()

    override fun start(startFuture: Future<Void>) {
        val streamName = config().getString("streamName")

        vertxClient = KinesisClientImpl(vertx, config())
        vertxClient.start {
            if (it.succeeded()) {

                vertxClient.describeStream(streamName, null, null) {
                    if (it.succeeded()) {
                        val shardIds = it.result().getJsonArray("shards").map { (it as? JsonObject)?.getString("shardId") }.filterNotNull()
                        val latch = CountDownLatch(shardIds.size())

                        shardIds.forEach { shardId ->
                            val shardVerticleConfig = config().copy().put("shardId", shardId)

                            vertx.deployVerticle(
                                ShardConsumerVerticleName,
                                DeploymentOptions().setConfig(shardVerticleConfig),
                                Handler {
                                    if (it.succeeded()) {
                                        shardVerticlesDeploymentIds.add(it.result())
                                        latch.countDown()
                                    } else {
                                        log.error("Can't start shard consumer verticle", it.cause())
                                    }
                                }
                            )
                        }

                        if (latch.await(10, TimeUnit.SECONDS)) {
                            log.info("Deployed ${ shardIds.size() } shard consumer verticles")
                            startFuture.complete()
                        } else {
                            startFuture.fail("Can't initialize shard consumers")
                        }
                    } else {
                        startFuture.fail(it.cause())
                    }
                }

                startFuture.complete()
            } else {
                startFuture.fail(it.cause())
            }
        }
    }

    override fun stop(stopFuture: Future<Void>) {
        // Undeploy client
        vertxClient.stop {
            if (it.succeeded()) {
                // Undeploy shard consumer verticles
                val latch = CountDownLatch(shardVerticlesDeploymentIds.size())

                shardVerticlesDeploymentIds.forEach { deploymentId ->
                    vertx.undeploy(deploymentId, Handler {
                        if (it.succeeded()) {
                            shardVerticlesDeploymentIds.remove(deploymentId)
                            latch.countDown()
                        } else {
                            log.error("Can't stop shard consumer verticle", it.cause())
                        }
                    })
                }

                if (latch.await(10, TimeUnit.SECONDS)) {
                    stopFuture.complete()
                } else {
                    stopFuture.fail("Can't stop shard listeners")
                }
            } else {
                stopFuture.fail(it.cause())
            }
        }
    }
}