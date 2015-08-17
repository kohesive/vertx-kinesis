package org.collokia.vertx.kinesis

import io.vertx.core.json.JsonObject

class KinesisMessageBusShardConsumerVerticle : AbstractKinesisShardConsumerVerticle() {

    private fun getAddress() = config().getString("address")

    override fun processRecords(records: List<JsonObject>) {
        records.forEach { recordJson ->
            vertx.eventBus().send(getAddress(), recordJson)
        }
    }
}