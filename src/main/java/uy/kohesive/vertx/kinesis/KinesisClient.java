package uy.kohesive.vertx.kinesis;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import uy.kohesive.vertx.kinesis.impl.KinesisClientImpl;

@VertxGen
public interface KinesisClient {

    static KinesisClient create(Vertx vertx, JsonObject config) {
        return new KinesisClientImpl(vertx, config);
    }

    void createStream(String streamName, Integer shardCount, Handler<AsyncResult<Object>> resultHandler);

    void describeStream(String streamName, Integer limit, String exclusiveStartShardId, Handler<AsyncResult<JsonObject>> resultHandler);

    void putRecord(String streamName, JsonObject record, Handler<AsyncResult<JsonObject>> resultHandler);

    void getShardIterator(String streamName, String shardId, String shardIteratorType, String startingSequenceNumber, Handler<AsyncResult<String>> resultHandler);

    void getRecords(String shardIterator, Integer limit, Handler<AsyncResult<JsonObject>> resultHandler);

    void start(Handler<AsyncResult<Void>> resultHandler);

    void stop(Handler<AsyncResult<Void>> resultHandler);

}
