## MQTT Client showcase

This project is a showcase of how to use MQTT as a client application.

In fact the project was created to demonstrate an implausible behavior of MQTT.

### starting position / use case

In a real project several applications subscribe (nearly at the same time because they are started in parallel) to the same MQTT topic (with wildcard). The topic contains about 500 retained messages (each in an own sub topic level) which contain some kind of configuration information. All applications are expected to receive these messages on every start (they are subscribing with QoS 1).

Beside the "configuration" messages also data topics are subscribed with the same MQTT connection. No persisted state is required (and wanted here). Therefore the application instances connect with `cleanSession=true`.

For my understanding it would be sufficient if the application instances would each connect with a fixed clientId as _cleanSession=true_ should avoid any state handling. But to be really sure that no state is considered  a `unique MQTT clientId` is generated for each connect. 

### observed behavior

Unfortunately not all application instances get the retained messages. Some get no messages at all from the topic - regardless of how long the subscription lasts. I first thought that the _maxInflight_ (client side) or _max_queued_messages_ (server side) configuration might be the reasons, but after increasing both to 500,000 I guess this is not the reasons behind the failure.

### reproduction as test

Therefore I created this project with a repro. There is a unit test class in the repro [MqttSubscriptionTest](src/test/java/de/frvabe/mqtt/client/showcase/MqttSubscriptionTest.java) with the test method `multiThreadSubscriptionTest`. When executing this test some (1000) retained messages will be published first in the `@BeforeClass` method. After that 10 instances of a `MqttSubscriber` class which implements the `IMqttMessageListener` and `Runnable` interface will be instantiated and executed. Each MqttSubscriber instance will be executed in an own thread with an own MqttClient instance and will subscribe to the topic tree with the retained messages. This is logged to the console as follows:

```
----------- perform subscriptions
Subscriber-3 subscribing topic 'mqtt/client/showcase/mutliThreadSubscription/#'
Subscriber-0 subscribing topic 'mqtt/client/showcase/mutliThreadSubscription/#'
Subscriber-2 subscribing topic 'mqtt/client/showcase/mutliThreadSubscription/#'
Subscriber-4 subscribing topic 'mqtt/client/showcase/mutliThreadSubscription/#'
Subscriber-5 subscribing topic 'mqtt/client/showcase/mutliThreadSubscription/#'
Subscriber-6 subscribing topic 'mqtt/client/showcase/mutliThreadSubscription/#'
Subscriber-1 subscribing topic 'mqtt/client/showcase/mutliThreadSubscription/#'
Subscriber-7 subscribing topic 'mqtt/client/showcase/mutliThreadSubscription/#'
Subscriber-8 subscribing topic 'mqtt/client/showcase/mutliThreadSubscription/#'
Subscriber-9 subscribing topic 'mqtt/client/showcase/mutliThreadSubscription/#'
```

The test will wait some time and after that validate the subscriptions. It is expected that each Subscriber received the 1000 retained messages:

```
----------- validate subscriptions
Subscriber-4: receivedMessages=1000; duration=372ms; succeeded=true
Subscriber-0: receivedMessages=1000; duration=265ms; succeeded=true
Subscriber-5: receivedMessages=1000; duration=475ms; succeeded=true
Subscriber-7: receivedMessages=0; duration=0ms; succeeded=false
Subscriber-6: receivedMessages=1000; duration=473ms; succeeded=true
Subscriber-8: receivedMessages=0; duration=0ms; succeeded=false
Subscriber-9: receivedMessages=1000; duration=346ms; succeeded=true
Subscriber-3: receivedMessages=1000; duration=243ms; succeeded=true
Subscriber-1: receivedMessages=1000; duration=470ms; succeeded=true
Subscriber-2: receivedMessages=1000; duration=357ms; succeeded=true
```

Most subscriber received the messages in a very short time (some hundreds ms). But some did not receive a single message (duration is 0 because they never finished). The situation is not better when giving the subscribers more time to receive the messages. They just won't get them.

I have no idea why this happens. If you can give any help this would be super useful for me, because I dependent on the reliable delivery of retained messages.

**Big thanks in advance!**

_By the way_: 

* I tested with a local EMQ and HiveMQ broker. If you want to run the test you need to run a broker on your machine at `localhost:1883` or change the configuration in the test class.
* I use the [Eclipse Paho Java Client](https://eclipse.org/paho/clients/java/) MQTT 