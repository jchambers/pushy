/*
 * Copyright (c) 2013-2017 Turo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.turo.pushy.apns.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Date;

import com.turo.pushy.apns.DeliveryPriority;
import org.junit.Assert;
import org.junit.Test;

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
        Assert.assertEquals(DeliveryPriority.IMMEDIATE, pushNotification.getPriority());
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
