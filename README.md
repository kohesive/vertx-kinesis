# Amazon Kinesis Client for Vert.x
This Vert.x client allows Amazon Kinesis access in three ways:

* As a @VertxGen service bridge to Amazon Kinesis Async Client methods
* As Amazon Kinesis stream/shard consuming verticles
* As a verticle listening to message bus and routing incoming messages to Kinesis stream

## Service usage

Client must be configured with a region. It can also be configured with AWS credentials, otherwise a default ~/.aws/credentials credentials file will be used:

```
JsonObject config = new JsonObject()
    .put("accessKey", "someAccessKey")
    .put("secretKey", "someSecretKey")
    .put("region", "us-west-2");
```

The client is initialized asynchronously:

```
KinesisClient client = KinesisClient.create(vertx, config);
client.start(result -> {
    if (result.succeeded()) {
        System.out.println("Client is initialized");
    }
});
```

Once the client is initialized, it can be used to access the Amazon SQS API in async manner:

```
client.createStream("MyStream", 2, result -> {
    if (result.succeeded()) {
        System.out.println("Message is sent");
    }
});
```        
        
Stream/shard consuming verticle usage

Kinesis stream/shard consumer verticles can be set up to process Kinesis records. A stream consumer verticle is deployed, which in turn deploys a shard consuming verticle for each stream's shard discovered.

The stream consuming verticle is deployed with a config containing AWS credentials (see above), region, stream name, and a name of shard consuming verticle to deploy for each shard:

```
JsonObject config = new JsonObject()
    .put("accessKey", "someAccessKey")
    .put("secretKey", "someSecretKey")
    .put("region", "us-west-2")
    .put("streamName", "MyStream")
    .put("shardConsumerVerticleName", "com.example.MyShardConsumingVerticle")

vertx.deployVerticle("org.collokia.vertx.kinesis.KinesisStreamConsumerVerticle", new DeploymentOptions().setConfig(config));    
```

