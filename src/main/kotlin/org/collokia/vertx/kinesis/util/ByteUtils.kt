package org.collokia.vertx.kinesis.util

import java.nio.ByteBuffer

fun ByteBuffer.toByteArray(): ByteArray {
    clear()
    val byteArray = ByteArray(this.capacity())
    get(byteArray)
    return byteArray
}