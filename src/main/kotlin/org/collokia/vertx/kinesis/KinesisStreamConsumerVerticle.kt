package org.collokia.vertx.kinesis

import com.collokia.vertx.util.getFromSharedMemoryAsync
import io.vertx.core.AsyncResult
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KinesisStreamConsumerVerticle : KinesisVerticle() {

    companion object {
        private val log = LoggerFactory.getLogger("KinesisStreamConsumerVerticle")
        private val ShardConsumerVerticleName = "org.collokia.vertx.kinesis.KinesisShardConsumerVerticle"
        private val ShardIteratorMapName = "kinesis-sharditerator"
    }

    private var shardVerticlesDeploymentIds = CopyOnWriteArrayList<String>()

    private fun getShardIteratorKey(shardId: String) = "${ getStreamName() }-$shardId"

    override fun startAfterClientInit(startFuture: Future<Void>) {
        vertxClient.describeStream(getStreamName(), null, null) {
            if (it.succeeded()) {
                val shardIds = it.result().getJsonArray("shards").map { (it as? JsonObject)?.getString("shardId") }.filterNotNull()
                val latch = CountDownLatch(shardIds.size())

                shardIds.forEach { shardId ->
                    vertx.getFromSharedMemoryAsync(ShardIteratorMapName, getShardIteratorKey(shardId), Handler { iteratorAsync: AsyncResult<String?> ->
                        if (iteratorAsync.succeeded()) {
                            val shardVerticleConfig = config().copy()
                                .put("shardId", shardId)
                                .put("shardIterator", iteratorAsync.result())

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
                        } else {
                            log.error("Can't retrieve shard iterator from shared memory", it.cause())
                        }
                    })
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
    }

    override fun stopAfterClientDispose(stopFuture: Future<Void>) {
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
    }

}