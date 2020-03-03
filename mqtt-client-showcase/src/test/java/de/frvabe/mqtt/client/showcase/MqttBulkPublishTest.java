package de.frvabe.mqtt.client.showcase;

import java.time.Duration;
import java.time.Instant;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;

public class MqttBulkPublishTest implements MqttCallbackExtended {

    private static final String[] mqttServerUris = new String[] {"tcp://localhost:1883"};

    @Test
    public void bulkPublish() throws MqttSecurityException, MqttException, InterruptedException {

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setServerURIs(mqttServerUris);
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(10);
        options.setMaxInflight(1000);

        MqttClient mqttClient =
                new MqttClient(mqttServerUris[0], "MQTT_CLIENT_TEST", new MemoryPersistence());
        mqttClient.connect(options);

        int msgCount = 500;

        System.out.println("Going to publish " + msgCount + " messages...");
        System.out.println();

        Instant start = Instant.now();
        for (int i = 1; i <= msgCount; i++) {
            String topic = "TEST/MqttBulkPublishTest/bulkPublish/" + i;
            System.out.println("publishing msg #" + i + " to topic '" + topic + "'");
            MqttMessage msg = new MqttMessage(new String("Test Message #" + i).getBytes());
            mqttClient.publish(topic, msg);
        }
        Instant stop = Instant.now();

        System.out.println();
        System.out.println("Published " + msgCount + " messages!");
        System.out.println("Start: " + start);
        System.out.println("Stop : " + stop);
        System.out.println("Duration: " + Duration.between(start, stop));
        System.out.println();

        System.out.println("\ndisconnect and close...");
        mqttClient.disconnect();
        mqttClient.close();

    }

    @Override
    public void connectionLost(Throwable cause) {
        System.out.println("connectionLost: " + cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        System.out.println("messageArrived: topic=" + topic + ", messageId=" + message.getId()
                + ", isRetained=" + message.isRetained());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        System.out.println("deliveryComplete: token=" + token);
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        System.out.println("connectComplete: serverURI=" + serverURI + ", reconnect=" + reconnect);
    }
}
