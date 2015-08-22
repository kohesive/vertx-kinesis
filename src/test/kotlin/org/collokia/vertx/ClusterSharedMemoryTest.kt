package org.collokia.vertx

import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import kotlin.platform.platformStatic
import kotlin.properties.Delegates

@RunWith(VertxUnitRunner::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ClusterSharedMemoryTest : SharedMemoryTest {

    companion object {
        var vertx: Vertx by Delegates.notNull()

        @BeforeClass
        @platformStatic
        fun before(context: TestContext) {
            val mgr = HazelcastClusterManager()
            val options = VertxOptions().setClusterManager(mgr)

            Vertx.clusteredVertx(options, context.asyncAssertSuccess() {
                vertx = it
            });
        }

        @AfterClass
        @platformStatic
        fun after(context: TestContext) {
            vertx.close(context.asyncAssertSuccess())
        }
    }

    @Test
    fun testClusterSharedMemory(context: TestContext) {
        testSharedMemoryUse(context)
    }

    override fun getVertx(): Vertx = vertx
}