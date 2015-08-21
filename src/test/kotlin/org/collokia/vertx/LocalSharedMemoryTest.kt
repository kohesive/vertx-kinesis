package org.collokia.vertx

import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import kotlin.platform.platformStatic
import kotlin.properties.Delegates

@RunWith(VertxUnitRunner::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LocalSharedMemoryTest : SharedMemoryTest {
    companion object {
        var vertx: Vertx = Vertx.vertx()

        @AfterClass
        @platformStatic
        fun after(context: TestContext) {
            vertx.close(context.asyncAssertSuccess())
        }
    }

    override fun getVertx() = vertx
}