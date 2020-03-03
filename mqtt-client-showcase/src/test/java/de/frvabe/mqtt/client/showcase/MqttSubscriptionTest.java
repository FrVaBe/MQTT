package de.frvabe.mqtt.client.showcase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Class to test the subscription of multiple threads (MQTT clients) to the same topic. It is
 * expected that all subscribers get all retained messages that were published to the topic which is
 * subscribed.
 */
public class MqttSubscriptionTest {

    private static final int MQTT_CLIENT_COUNT = 10;
    private static final int TEST_MESSAGE_COUNT = 1000;
    private static final String TEST_TOPIC = "mqtt/client/showcase/mutliThreadSubscription";
    private static final String[] mqttServerUris = new String[] {"tcp://localhost:1883"};

    /**
     * A subscriber Thread. This will be used to subscribe to a topic and is expected to receive all
     * retained messages.
     */
    public static class MqttSubscriber implements IMqttMessageListener, Runnable {

        private Set<String> messages = new HashSet<>();
        private long duration;

        /**
         * The callback method of the MQTT message listener. Messages of subscribed topics will be
         * received here.
         */
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            String msg = new String(message.getPayload());
            // System.out
            // .println(Thread.currentThread().getName() + " received message: '" + msg + "'");
            messages.add(msg);
        }

        public void run() {

            MqttClient subscriberMqttClient = null;
            String subscribeTopic = TEST_TOPIC + "/#";
            long start = System.currentTimeMillis();

            try {
                subscriberMqttClient = getConnectedMqttClient();
                System.out.println(Thread.currentThread().getName() + " subscribing topic '"
                        + subscribeTopic + "'");
                subscriberMqttClient.subscribe(subscribeTopic, this);
            } catch (MqttException ex) {
                System.out.println("Error while subsribing to topic '" + subscribeTopic
                        + "' (Thread=" + Thread.currentThread().getName() + "; clientId='"
                        + subscriberMqttClient.getClientId() + "':" + ex.getMessage());
            }

            while (messages.size() < TEST_MESSAGE_COUNT) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            duration = System.currentTimeMillis() - start;
            silentClose(subscriberMqttClient);

        }

        public boolean succeeded() {
            return messages.size() >= TEST_MESSAGE_COUNT;
        }

        public int getReceivedMsgCount() {
            return messages.size();
        }

        public long getDuration() {
            return duration;
        }

    }

    /**
     * The method that should run before the test class gets instantiated. The retained messages
     * which are expected to be received by the subscriber during the test are published here.
     * 
     * @throws MqttException in case of any MQTT exceptions
     */
    @BeforeAll
    public static void beforeClass() throws MqttException {

        MqttClient mqttClient = getConnectedMqttClient();

        for (int i = 0; i < TEST_MESSAGE_COUNT; i++) {
            String topic = TEST_TOPIC + "/" + i;
            String msg = "Hello World: " + i;
            // System.out.println("publishing topic='" + topic + "'; msg='" + msg + "'");
            mqttClient.publish(topic, msg.getBytes(), 1, true);
        }

        silentClose(mqttClient);

    }

    /**
     * Remove all generated retained messages.
     * 
     * @throws MqttException in case of any MQTT exceptions
     */
    @AfterAll
    public static void afterClass() throws MqttException {

        MqttClient mqttClient = getConnectedMqttClient();
        byte[] emptyMessage = new byte[] {};

        for (int i = 0; i < TEST_MESSAGE_COUNT; i++) {
            String topic = TEST_TOPIC + "/" + i;
            mqttClient.publish(topic, emptyMessage, 1, true);
        }

        silentClose(mqttClient);

    }

    /**
     * A test where multiple MQTT client threads subscribe to the same topic at (nearly) the same
     * time. It is expected that all MQTT clients receive all retained methods.
     * 
     * @throws InterruptedException in case of {@link MqttClient} failures
     */
    @Test
    public void multiThreadSubscriptionTest() throws InterruptedException {

        Map<Thread, MqttSubscriber> subscriberThreads = new HashMap<>();

        System.out.println("----------- perform subscriptions");

        // perform some subscriptions in different threads to the same topic
        for (int i = 0; i < MQTT_CLIENT_COUNT; i++) {
            MqttSubscriber subscriber = new MqttSubscriber();
            Thread thread = new Thread(subscriber, "Subscriber-" + i);
            subscriberThreads.put(thread, subscriber);
            thread.start();
        }

        // sleep some time; it should be no problem for all subscriber threads to receive the
        // retained messages during this time
        Thread.sleep(10000);

        System.out.println("----------- validate subscriptions");
        boolean succeeded = true;

        // validate the assumption that all subscriptions have received all retained messages
        for (Entry<Thread, MqttSubscriber> subscriberThread : subscriberThreads.entrySet()) {
            Thread thread = subscriberThread.getKey();
            MqttSubscriber subscriber = subscriberThread.getValue();
            System.out.println(thread.getName() + ": receivedMessages="
                    + subscriber.getReceivedMsgCount() + "; duration=" + subscriber.getDuration()
                    + "ms; succeeded=" + subscriber.succeeded());
            succeeded = succeeded && subscriber.succeeded();
        }

        // we expect that all threads completed and received all retained messages
        assertTrue(succeeded);
    }

    /**
     * A test where one MQTT client threads subscribe to the same topic at (nearly) the same time.
     * It is expected that all MQTT clients receive all retained methods.
     * 
     * @throws InterruptedException in case of {@link MqttClient} failures
     */
    @Test
    public void singleThreadSubscriptionTest() throws InterruptedException {

        MqttSubscriber subscriber = new MqttSubscriber();
        Thread thread = new Thread(subscriber, "MqttSubscriber");
        thread.start();
        Thread.sleep(2000);
        System.out.println(thread.getName() + ": receivedMessages="
                + subscriber.getReceivedMsgCount() + "; duration=" + subscriber.getDuration()
                + "ms; succeeded=" + subscriber.succeeded());
        assertFalse(thread.isAlive());
        assertTrue(subscriber.succeeded());

    }

    /**
     * Gets a new MQTT client. The client is already connected.
     * 
     * @return a new connected MQTT client
     * @throws MqttException in case of any MQTT exception
     */
    public static MqttClient getConnectedMqttClient() throws MqttException {
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        connOpts.setServerURIs(mqttServerUris);
        connOpts.setAutomaticReconnect(true);
        connOpts.setMaxInflight(500_000);
        connOpts.setKeepAliveInterval(60);

        String mqttClientId = "MTST" + "-" + UUID.randomUUID().toString().replaceAll("-", "");
        MqttClient mqttClient =
                new MqttClient(mqttServerUris[0], mqttClientId, new MemoryPersistence());
        mqttClient.connect(connOpts);

        return mqttClient;

    }

    /**
     * Silently (do not throw any exceptions) disconnect and close an mqttClient.
     * 
     * @param mqttClient the MQTT client
     */
    public static void silentClose(final MqttClient mqttClient) {
        if (mqttClient != null) {
            try {
                mqttClient.disconnect();
            } catch (MqttException ignore) {
            } finally {
                try {
                    mqttClient.close();
                } catch (MqttException ignore) {

                }
            }
        }
    }

}
