package com.relayrides.pushy.apns.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Date;

import org.junit.Test;

import com.relayrides.pushy.apns.DeliveryPriority;

public class SimpleApnsPushNotificationTest {

    @Test
    public void testSimpleApnsPushNotificationTokenTopicPayload() {
        final String token = "test-token";
        final String topic = "test-topic";
        final String payload = "{\"test\": true}";

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(token, topic, payload);

        assertEquals(token, pushNotification.getToken());
        assertEquals(topic, pushNotification.getTopic());
        assertEquals(payload, pushNotification.getPayload());
        assertNull(pushNotification.getExpiration());
        assertEquals(DeliveryPriority.IMMEDIATE, pushNotification.getPriority());
        assertNull(pushNotification.getCollapseId());
    }

    @Test(expected = NullPointerException.class)
    public void testSimpleApnsPushNotificationNullToken() {
        new SimpleApnsPushNotification(null, "topic", "payload");
    }

    @Test(expected = NullPointerException.class)
    public void testSimpleApnsPushNotificationNullTopic() {
        new SimpleApnsPushNotification("token", null, "payload");
    }

    @Test(expected = NullPointerException.class)
    public void testSimpleApnsPushNotificationNullPayload() {
        new SimpleApnsPushNotification("token", "topic", null);
    }

    @Test
    public void testSimpleApnsPushNotificationTokenTopicPayloadExpiration() {
        final String token = "test-token";
        final String topic = "test-topic";
        final String payload = "{\"test\": true}";
        final Date expiration = new Date();

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(token, topic, payload, expiration);

        assertEquals(token, pushNotification.getToken());
        assertEquals(topic, pushNotification.getTopic());
        assertEquals(payload, pushNotification.getPayload());
        assertEquals(expiration, pushNotification.getExpiration());
        assertEquals(DeliveryPriority.IMMEDIATE, pushNotification.getPriority());
        assertNull(pushNotification.getCollapseId());
    }

    @Test
    public void testSimpleApnsPushNotificationTokenTopicPayloadExpirationPriority() {
        final String token = "test-token";
        final String topic = "test-topic";
        final String payload = "{\"test\": true}";
        final Date expiration = new Date();
        final DeliveryPriority priority = DeliveryPriority.CONSERVE_POWER;

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(token, topic, payload, expiration, priority);

        assertEquals(token, pushNotification.getToken());
        assertEquals(topic, pushNotification.getTopic());
        assertEquals(payload, pushNotification.getPayload());
        assertEquals(expiration, pushNotification.getExpiration());
        assertEquals(priority, pushNotification.getPriority());
        assertNull(pushNotification.getCollapseId());
    }

    @Test
    public void testSimpleApnsPushNotificationTokenTopicPayloadExpirationPriorityCollapseId() {
        final String token = "test-token";
        final String topic = "test-topic";
        final String payload = "{\"test\": true}";
        final Date expiration = new Date();
        final DeliveryPriority priority = DeliveryPriority.CONSERVE_POWER;
        final String collapseId = "test-collapse-id";

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(token, topic, payload, expiration, priority, collapseId);

        assertEquals(token, pushNotification.getToken());
        assertEquals(topic, pushNotification.getTopic());
        assertEquals(payload, pushNotification.getPayload());
        assertEquals(expiration, pushNotification.getExpiration());
        assertEquals(priority, pushNotification.getPriority());
        assertEquals(collapseId, pushNotification.getCollapseId());
    }
}
