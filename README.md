# Amazon Kinesis Client for Vert.x
This Vert.x client allows Amazon Kinesis access in three ways:

* As a @VertxGen service bridge to Amazon Kinesis Async Client methods
* As Amazon Kinesis stream/shard consuming verticles
* As a verticle listening to message bus and routing incoming messages to Kinesis stream

### Gradle /Maven

Add add the following dependency:

```
uy.kohesive.vertx:vertx-kensis:1.0.0-BETA-01
```


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

Once the client is initialized, it can be used to access the Amazon Kinesis API in async manner:

```
client.createStream("MyStream", 2, result -> {
    if (result.succeeded()) {
        System.out.println("Message is sent");
    }
});
```        
        
## Stream/shard consuming verticle usage

Kinesis stream/shard consumer verticles can be set up to process Kinesis records. A stream consumer verticle is deployed, which in turn deploys a shard consuming verticle for each stream's shard discovered.

The stream consuming verticle is deployed with a config containing AWS credentials (see above), region, stream name, and a name of shard consuming verticle to deploy for each shard:

```
JsonObject config = new JsonObject()
    .put("accessKey", "someAccessKey")
    .put("secretKey", "someSecretKey")
    .put("region", "us-west-2")
    .put("streamName", "MyStream")
    .put("shardConsumerVerticleName", "com.example.MyShardConsumingVerticle")

vertx.deployVerticle("uy.kohesive.vertx.kinesis.KinesisStreamConsumerVerticle", new DeploymentOptions().setConfig(config));    
```

When the stream verticle is deployed, it deploys a shard verticle for each stream's shard. The verticle to be deployed this way must be a subclass of `uy.kohesive.vertx.kinesis.AbstractKinesisShardConsumerVerticle`:

```
public class MyShardConsumingVerticle extends AbstractKinesisShardConsumerVerticle {
    @Override
    protected void processRecords(List<? extends JsonObject> records) {
        for (JsonObject record : records) {
            System.out.println(record);
        }
    }
}
```

It's deployed with a copy of a configuration passed to stream verticle plus shard metadata, so you can use the stream verticle configuration to pass the configuration data to the shard verticles.

### Shard iterators

In order to avoid consuming the same record twice, shard iterator is stored in Vertx's cluster memory (or local memory in case of no cluster present) async map named `kinesis-sharditerator` with keys following the pattern `STREAM_NAME-SHARD_ID`.

## Record producer verticle usage

A record producer verticle listens to the event bus address configured and puts the incoming records to the Kinesis stream. It's configured with AWS credentials, stream name and an address to listen to:

```
JsonObject config = new JsonObject()
    .put("accessKey", "someAccessKey")
    .put("secretKey", "someSecretKey")
    .put("region", "us-west-2")
    .put("streamName", "MyStream")
    .put("address", "kinesis.stream.MyStream");
    
vertx.deployVerticle("uy.kohesive.vertx.kinesis.KinesisStreamProducerVerticle", new DeploymentOptions().setConfig(config));    
```

To submit a record:

```
JsonObject record = new JsonObject()
    .put("data", "Hello World".getBytes("UTF-8"))
    .put("partitionKey", "someKey");

vertx.eventBus().send("kinesis.stream.MyStream", record);
```

## Building from sources

vertx-kinesis unit tests rely on empty local Kinesis instance. In order for them to work, the build script installs and runs [Kinesalite](https://github.com/mhart/kinesalite), a Node.js Kinesis implementation. Currently, the gradle build script simply tries to install Kinesalite using `npm install -g kinesalite`, then run it, and stop it after tests by locating a process by open port. Which obviously needs [npm](https://www.npmjs.com) installed.
