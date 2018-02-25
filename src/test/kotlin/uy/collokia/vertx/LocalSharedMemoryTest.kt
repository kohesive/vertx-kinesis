package uy.collokia.vertx

import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.AfterClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(VertxUnitRunner::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LocalSharedMemoryTest : SharedMemoryTest {
    companion object {
        var vertx: Vertx = Vertx.vertx()

        @AfterClass
        @JvmStatic
        fun after(context: TestContext) {
            vertx.close(context.asyncAssertSuccess())
        }
    }

    @Test
    fun testLocalSharedMemory(context: TestContext) {
        testSharedMemoryUse(context)
    }

    override fun getVertx(): Vertx = vertx
}