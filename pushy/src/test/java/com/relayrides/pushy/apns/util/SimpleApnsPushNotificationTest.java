package com.relayrides.pushy.apns.util;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

import com.relayrides.pushy.apns.DeliveryPriority;

public class SimpleApnsPushNotificationTest {

    private static final String TOKEN = "test-token";
    private static final String TOPIC = "test-topic";
    private static final String PAYLOAD = "{ testPayload: true }";

    @Test
    public void testSimpleApnsPushNotificationStringStringString() {
        final SimpleApnsPushNotification notification = new SimpleApnsPushNotification(TOKEN, TOPIC, PAYLOAD);

        assertEquals(TOKEN, notification.getToken());
        assertEquals(TOPIC, notification.getTopic());
        assertEquals(PAYLOAD, notification.getPayload());
        assertNull(notification.getExpiration());
        assertEquals(DeliveryPriority.IMMEDIATE, notification.getPriority());
    }

    @Test
    public void testSimpleApnsPushNotificationStringStringStringDate() {
        final Date expiration = new Date();

        final SimpleApnsPushNotification notification = new SimpleApnsPushNotification(TOKEN, TOPIC, PAYLOAD, expiration);

        assertEquals(TOKEN, notification.getToken());
        assertEquals(TOPIC, notification.getTopic());
        assertEquals(PAYLOAD, notification.getPayload());
        assertEquals(expiration, notification.getExpiration());
        assertEquals(DeliveryPriority.IMMEDIATE, notification.getPriority());
    }

    @Test
    public void testSimpleApnsPushNotificationStringStringStringDateDeliveryPriority() {
        final Date expiration = new Date();
        final DeliveryPriority priority = DeliveryPriority.CONSERVE_POWER;

        final SimpleApnsPushNotification notification = new SimpleApnsPushNotification(TOKEN, TOPIC, PAYLOAD, expiration, priority);

        assertEquals(TOKEN, notification.getToken());
        assertEquals(TOPIC, notification.getTopic());
        assertEquals(PAYLOAD, notification.getPayload());
        assertEquals(expiration, notification.getExpiration());
        assertEquals(priority, notification.getPriority());
    }

    @Test(expected = NullPointerException.class)
    public void testSimpleApnsPushNotificationNullToken() {
        new SimpleApnsPushNotification(null, TOPIC, PAYLOAD, null, DeliveryPriority.CONSERVE_POWER);
    }

    @Test(expected = NullPointerException.class)
    public void testSimpleApnsPushNotificationNullTopic() {
        new SimpleApnsPushNotification(TOKEN, null, PAYLOAD, null, DeliveryPriority.CONSERVE_POWER);
    }

    @Test(expected = NullPointerException.class)
    public void testSimpleApnsPushNotificationNullPayload() {
        new SimpleApnsPushNotification(TOKEN, TOPIC, null, null, DeliveryPriority.CONSERVE_POWER);
    }

    @Test(expected = NullPointerException.class)
    public void testSimpleApnsPushNotificationNullPriority() {
        new SimpleApnsPushNotification(TOKEN, TOPIC, PAYLOAD, null, null);
    }
}
