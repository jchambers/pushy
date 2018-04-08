/*
 * Copyright (c) 2013-2018 Turo
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

package com.turo.pushy.apns.metrics.micrometer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MicrometerApnsClientMetricsListenerTest {

    private MeterRegistry meterRegistry;
    private MicrometerApnsClientMetricsListener listener;

    @Before
    public void setUp() {
        this.meterRegistry = new SimpleMeterRegistry();
        this.listener = new MicrometerApnsClientMetricsListener(this.meterRegistry);
    }

    @Test
    public void testHandleWriteFailure() {
        final Counter writeFailures = this.meterRegistry.get(MicrometerApnsClientMetricsListener.WRITE_FAILURES_COUNTER_NAME).counter();
        assertEquals(0, (int) writeFailures.count());

        this.listener.handleWriteFailure(null, 1);
        assertEquals(1, (int) writeFailures.count());
    }

    @Test
    public void testHandleNotificationSent() {
        final Counter sentNotifications = this.meterRegistry.get(MicrometerApnsClientMetricsListener.SENT_NOTIFICATIONS_COUNTER_NAME).counter();
        assertEquals(0, (int) sentNotifications.count());

        this.listener.handleNotificationSent(null, 1);
        assertEquals(1, (int) sentNotifications.count());
    }

    @Test
    public void testHandleNotificationAccepted() {
        final Counter acceptedNotifications = this.meterRegistry.get(MicrometerApnsClientMetricsListener.ACCEPTED_NOTIFICATIONS_COUNTER_NAME).counter();
        assertEquals(0, (int) acceptedNotifications.count());

        this.listener.handleNotificationAccepted(null, 1);
        assertEquals(1, (int) acceptedNotifications.count());
    }

    @Test
    public void testHandleNotificationRejected() {
        final Counter rejectedNotifications = this.meterRegistry.get(MicrometerApnsClientMetricsListener.REJECTED_NOTIFICATIONS_COUNTER_NAME).counter();
        assertEquals(0, (int) rejectedNotifications.count());

        this.listener.handleNotificationRejected(null, 1);
        assertEquals(1, (int) rejectedNotifications.count());
    }

    @Test
    public void testHandleConnectionAddedAndRemoved() {
        final Gauge openConnectionGauge = this.meterRegistry.get(MicrometerApnsClientMetricsListener.OPEN_CONNECTIONS_GAUGE_NAME).gauge();

        this.listener.handleConnectionAdded(null);

        assertEquals(1, (int) openConnectionGauge.value());

        this.listener.handleConnectionRemoved(null);

        assertEquals(0, (int) openConnectionGauge.value());
    }

    @Test
    public void testHandleConnectionCreationFailed() {
        final Counter connectionFailures = this.meterRegistry.get(MicrometerApnsClientMetricsListener.CONNECTION_FAILURES_COUNTER_NAME).counter();
        assertEquals(0, (int) connectionFailures.count());

        this.listener.handleConnectionCreationFailed(null);
        assertEquals(1, (int) connectionFailures.count());
    }
}
