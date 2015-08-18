package org.collokia.vertx.kinesis

import com.collokia.vertx.util.getFromSharedMemoryAsync
import com.collokia.vertx.util.onSuccess
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KinesisStreamConsumerVerticle : KinesisVerticle() {

    companion object {
        private val log = LoggerFactory.getLogger("KinesisStreamConsumerVerticle")
    }

    private var shardVerticlesDeploymentIds = CopyOnWriteArrayList<String>()

    override fun startAfterClientInit(startFuture: Future<Void>) {
        vertxClient.describeStream(getStreamName(), null, null, onSuccess<JsonObject> {
            val shardIds = it.getJsonArray("shards").map { (it as? JsonObject)?.getString("shardId") }.filterNotNull()
            val latch = CountDownLatch(shardIds.size())

            for (shardId in shardIds) {
                vertx.getFromSharedMemoryAsync(KinesisVerticle.ShardIteratorMapName, getShardIteratorKey(shardId), onSuccess<String?> {
                        val shardVerticleConfig = config().copy()
                            .put("shardId", shardId)
                            .put("shardIterator", it)

                        vertx.deployVerticle(
                            config().getString("shardConsumerVerticleName"),
                            DeploymentOptions().setConfig(shardVerticleConfig),
                            onSuccess<String> {
                                shardVerticlesDeploymentIds.add(it)
                                latch.countDown()
                            } onFail {
                                log.error("Can't start shard consumer verticle", it)
                            }
                        )
                    } onFail {
                        log.error("Can't retrieve shard iterator from shared memory", it)
                    })
            }

            if (latch.await(10, TimeUnit.SECONDS)) {
                log.info("Deployed ${ shardIds.size() } shard consumer verticles")
                startFuture.complete()
            } else {
                startFuture.fail("Can't initialize shard consumers")
            }
        } onFail {
            startFuture.fail(it)
        })
    }

    override fun stopAfterClientDispose(stopFuture: Future<Void>) {
        // Undeploy shard consumer verticles
        val latch = CountDownLatch(shardVerticlesDeploymentIds.size())

        shardVerticlesDeploymentIds.forEach { deploymentId ->
            vertx.undeploy(deploymentId, onSuccess<Void> {
                shardVerticlesDeploymentIds.remove(deploymentId)
                latch.countDown()
            } onFail {
                log.error("Can't stop shard consumer verticle", it)
            })
        }

        if (latch.await(10, TimeUnit.SECONDS)) {
            stopFuture.complete()
        } else {
            stopFuture.fail("Can't stop shard listeners")
        }
    }

}