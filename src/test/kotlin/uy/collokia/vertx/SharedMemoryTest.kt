package uy.collokia.vertx

import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import uy.collokia.vertx.util.getFromSharedMemoryAsync
import uy.collokia.vertx.util.putToSharedMemoryAsync
import org.junit.Ignore
import org.junit.Test

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