package org.collokia.vertx

import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import org.collokia.vertx.util.getFromSharedMemoryAsync
import org.collokia.vertx.util.putToSharedMemoryAsync
import org.junit.Ignore
import org.junit.Test

@Ignore
internal interface SharedMemoryTest {

    fun getVertx(): Vertx

    protected fun testSharedMemoryUse(context: TestContext) {
        getVertx().putToSharedMemoryAsync("someMap", "someKey", "someValue", context.asyncAssertSuccess() {
            getVertx().getFromSharedMemoryAsync("someMap", "someKey", context.asyncAssertSuccess<String?>() {
                context.assertEquals("someValue", it)
            })
        })
    }

}