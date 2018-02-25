package uy.kohesive.vertx

import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import uy.kohesive.vertx.util.getFromSharedMemoryAsync
import uy.kohesive.vertx.util.putToSharedMemoryAsync
import org.junit.Ignore

@Ignore
internal interface SharedMemoryTest {

    fun getVertx(): Vertx

    fun testSharedMemoryUse(context: TestContext) {
        getVertx().putToSharedMemoryAsync("someMap", "someKey", "someValue", context.asyncAssertSuccess() {
            getVertx().getFromSharedMemoryAsync("someMap", "someKey", context.asyncAssertSuccess<String?>() {
                context.assertEquals("someValue", it)
            })
        })
    }

}