package org.collokia.vertx.util

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.impl.VertxInternal
import io.vertx.core.shareddata.AsyncMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap


fun <K : Any, V : Any> Vertx.getFromSharedMemoryAsync(mapName: String, key: K, resultHandler: Handler<AsyncResult<V?>>) {
    if (isClustered) {
        (this as VertxInternal).clusterManager.getAsyncMap(mapName, Handler { mapAsyncResult: AsyncResult<AsyncMap<K, V>> ->
            if (mapAsyncResult.succeeded()) {
                val asyncMap = mapAsyncResult.result()
                asyncMap.get(key, Handler { getAsyncResult: AsyncResult<V> ->
                    if (getAsyncResult.succeeded()) {
                        resultHandler.handle(Future.succeededFuture(getAsyncResult.result()))
                    } else {
                        resultHandler.handle(Future.failedFuture(getAsyncResult.cause()))
                    }
                })
            } else {
                resultHandler.handle(Future.failedFuture(mapAsyncResult.cause()))
            }
        })
    } else {
        resultHandler.handle(Future.succeededFuture(LocalMaps.get(mapName, key)))
    }
}

fun <K : Any, V : Any> Vertx.putToSharedMemoryAsync(mapName: String, key: K, value: V, resultHandler: Handler<AsyncResult<Void?>>) {
    if (isClustered) {
        (this as VertxInternal).clusterManager.getAsyncMap(mapName, Handler { mapAsyncResult: AsyncResult<AsyncMap<K, V>> ->
            if (mapAsyncResult.succeeded()) {
                val asyncMap = mapAsyncResult.result()
                asyncMap.put(key, value) {
                    if (it.succeeded()) {
                        resultHandler.handle(Future.succeededFuture())
                    } else {
                        resultHandler.handle(Future.failedFuture(it.cause()))
                    }
                }
            } else {
                resultHandler.handle(Future.failedFuture(mapAsyncResult.cause()))
            }
        })
    } else {
        LocalMaps.put(mapName, key, value)
        resultHandler.handle(Future.succeededFuture())
    }
}

object LocalMaps {

    private val map = ConcurrentHashMap<String, ConcurrentMap<Any, Any>>()

    fun <K : Any, V : Any> get(mapName: String, key: K): V? = map.get(mapName)?.get(key) as V?

    fun <K : Any, V : Any> put(mapName: String, key: K, value: V) {
        map.putIfAbsent(mapName, ConcurrentHashMap<Any, Any>().let {
            it.put(key, value); it
        })
    }

}