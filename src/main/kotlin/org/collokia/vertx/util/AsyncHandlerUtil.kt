package org.collokia.vertx.util

import io.vertx.core.AsyncResult
import io.vertx.core.Handler

fun <T> onSuccess(success: (T) -> Unit) = AsyncResultHandler(success)

data class AsyncResultHandler<T>(
    val success: (T) -> Unit = {},
    val fail: (Throwable) -> Unit = {}
) : Handler<AsyncResult<T>> {

    override fun handle(event: AsyncResult<T>) {
        if (event.succeeded()) {
            success(event.result())
        } else {
            fail(event.cause())
        }
    }

    fun onFail(newFail: (Throwable) -> Unit): AsyncResultHandler<T> = copy(success = success, fail = newFail)

}