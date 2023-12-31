/*
 * Copyright (c) 2020 Jon Chambers
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

package com.eatthepath.pushy.apns.metrics.micrometer;

import com.eatthepath.pushy.apns.ApnsPushNotification;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


public class MicrometerApnsClientMetricsListenerTest {

    private MeterRegistry meterRegistry;
    private MicrometerApnsClientMetricsListener listener;

    @BeforeEach
    public void setUp() {
        this.meterRegistry = new SimpleMeterRegistry();
        this.listener = new MicrometerApnsClientMetricsListener(this.meterRegistry);
    }

    @Test
    public void testMicrometerApnsClientMetricsListener() {
        assertDoesNotThrow(() -> new MicrometerApnsClientMetricsListener(new SimpleMeterRegistry()));
        assertDoesNotThrow(() -> new MicrometerApnsClientMetricsListener(new SimpleMeterRegistry(), "tag", "value"));
        assertDoesNotThrow(() -> new MicrometerApnsClientMetricsListener(new SimpleMeterRegistry(), Tags.of("tag", "value")));
        assertDoesNotThrow(() -> new MicrometerApnsClientMetricsListener(new SimpleMeterRegistry(), (List<Tag>) null));
    }

    @Test
    public void testHandleWriteFailure() {
        this.listener.handleWriteFailure("com.example.topic");
        assertEquals(1, (int) this.meterRegistry.get(MicrometerApnsClientMetricsListener.WRITE_FAILURES_COUNTER_NAME).counter().count());
    }

    @Test
    public void testHandleNotificationSent() {
        this.listener.handleNotificationSent("com.example.topic");
        assertEquals(1, (int) this.meterRegistry.get(MicrometerApnsClientMetricsListener.SENT_NOTIFICATIONS_COUNTER_NAME).counter().count());
    }

    @Test
    public void testHandleNotificationAccepted() {
        final String topic = "com.example.topic";
        final int status = 200;

        this.listener.handleNotificationAcknowledged(buildPushNotificationResponse(topic, true, status, null), 1);

        final Timer acknowledgedNotifications = this.meterRegistry
            .get(MicrometerApnsClientMetricsListener.ACKNOWLEDGED_NOTIFICATIONS_TIMER_NAME)
            .tags(MicrometerApnsClientMetricsListener.TOPIC_TAG_NAME, topic,
                MicrometerApnsClientMetricsListener.ACCEPTED_TAG_NAME, String.valueOf(true),
                MicrometerApnsClientMetricsListener.STATUS_TAG_NAME, String.valueOf(status))
            .timer();

        assertEquals(1, (int) acknowledgedNotifications.count());
    }

    @Test
    public void testHandleNotificationRejected() {
        final String topic = "com.example.topic";
        final int status = 400;
        final String rejectionReason = "BadDeviceToken";

        this.listener.handleNotificationAcknowledged(buildPushNotificationResponse(topic, false, status, rejectionReason), 1);

        final Timer acknowledgedNotifications = this.meterRegistry
            .get(MicrometerApnsClientMetricsListener.ACKNOWLEDGED_NOTIFICATIONS_TIMER_NAME)
            .tags(MicrometerApnsClientMetricsListener.TOPIC_TAG_NAME, topic,
                MicrometerApnsClientMetricsListener.ACCEPTED_TAG_NAME, String.valueOf(false),
                MicrometerApnsClientMetricsListener.STATUS_TAG_NAME, String.valueOf(status),
                MicrometerApnsClientMetricsListener.REASON_TAG_NAME, rejectionReason)
            .timer();

        assertEquals(1, (int) acknowledgedNotifications.count());
    }

    @Test
    public void testHandleConnectionAddedAndRemoved() {
        this.listener.handleConnectionAdded();

        assertEquals(1, (int) this.meterRegistry.get(MicrometerApnsClientMetricsListener.OPEN_CONNECTIONS_GAUGE_NAME).gauge().value());

        this.listener.handleConnectionRemoved();

        assertEquals(0, (int) this.meterRegistry.get(MicrometerApnsClientMetricsListener.OPEN_CONNECTIONS_GAUGE_NAME).gauge().value());
    }

    @Test
    public void testHandleConnectionCreationFailed() {
        this.listener.handleConnectionCreationFailed();
        assertEquals(1, (int) this.meterRegistry.get(MicrometerApnsClientMetricsListener.CONNECTION_FAILURES_COUNTER_NAME).counter().count());
    }

    private static PushNotificationResponse<?> buildPushNotificationResponse(final String topic,
                                                                             final boolean accepted,
                                                                             final int status,
                                                                             final String rejectionReason) {

        return new PushNotificationResponse<ApnsPushNotification>() {
            @Override
            public ApnsPushNotification getPushNotification() {
                return new SimpleApnsPushNotification("device-token", topic, "{}");
            }

            @Override
            public boolean isAccepted() {
                return accepted;
            }

            @Override
            public UUID getApnsId() {
                return null;
            }

            @Override
            public int getStatusCode() {
                return status;
            }

            @Override
            public Optional<String> getRejectionReason() {
                return Optional.ofNullable(rejectionReason);
            }

            @Override
            public Optional<Instant> getTokenInvalidationTimestamp() {
                return Optional.empty();
            }
        };
    }
}
