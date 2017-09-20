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

package com.turo.pushy.apns.metrics.dropwizard;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DropwizardApnsClientMetricsListenerTest {

    private DropwizardApnsClientMetricsListener listener;

    @Before
    public void setUp() throws Exception {
        this.listener = new DropwizardApnsClientMetricsListener();
    }

    @Test
    public void testHandleWriteFailure() {
        final Meter writeFailures = (Meter) this.listener.getMetrics().get(DropwizardApnsClientMetricsListener.WRITE_FAILURES_METER_NAME);
        assertEquals(0, writeFailures.getCount());

        this.listener.handleWriteFailure(null, 1);
        assertEquals(1, writeFailures.getCount());
    }

    @Test
    public void testHandleNotificationSent() {
        final Meter sentNotifications = (Meter) this.listener.getMetrics().get(DropwizardApnsClientMetricsListener.SENT_NOTIFICATIONS_METER_NAME);
        assertEquals(0, sentNotifications.getCount());

        this.listener.handleNotificationSent(null, 1);
        assertEquals(1, sentNotifications.getCount());
    }

    @Test
    public void testHandleNotificationAccepted() {
        final Meter acceptedNotifications = (Meter) this.listener.getMetrics().get(DropwizardApnsClientMetricsListener.ACCEPTED_NOTIFICATIONS_METER_NAME);
        assertEquals(0, acceptedNotifications.getCount());

        this.listener.handleNotificationAccepted(null, 1);
        assertEquals(1, acceptedNotifications.getCount());
    }

    @Test
    public void testHandleNotificationRejected() {
        final Meter rejectedNotifications = (Meter) this.listener.getMetrics().get(DropwizardApnsClientMetricsListener.REJECTED_NOTIFICATIONS_METER_NAME);
        assertEquals(0, rejectedNotifications.getCount());

        this.listener.handleNotificationRejected(null, 1);
        assertEquals(1, rejectedNotifications.getCount());
    }

    @Test
    public void testHandleConnectionAddedAndRemoved() {
        @SuppressWarnings("unchecked")
        final Gauge<Integer> openConnectionGauge = (Gauge<Integer>) this.listener.getMetrics().get(DropwizardApnsClientMetricsListener.OPEN_CONNECTIONS_GAUGE_NAME);

        this.listener.handleConnectionAdded(null);

        assertEquals(1, (long) openConnectionGauge.getValue());

        this.listener.handleConnectionRemoved(null);

        assertEquals(0, (long) openConnectionGauge.getValue());
    }

    @Test
    public void testHandleConnectionCreationFailed() {
        final Meter connectionFailures = (Meter) this.listener.getMetrics().get(DropwizardApnsClientMetricsListener.CONNECTION_FAILURES_METER_NAME);

        this.listener.handleConnectionCreationFailed(null);

        assertEquals(1, connectionFailures.getCount());
    }

    @Test
    public void testGetMetrics() {
        final Map<String, Metric> metrics = this.listener.getMetrics();

        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.NOTIFICATION_TIMER_NAME) instanceof Timer);

        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.WRITE_FAILURES_METER_NAME) instanceof Meter);
        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.SENT_NOTIFICATIONS_METER_NAME) instanceof Meter);
        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.ACCEPTED_NOTIFICATIONS_METER_NAME) instanceof Meter);
        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.REJECTED_NOTIFICATIONS_METER_NAME) instanceof Meter);

        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.OPEN_CONNECTIONS_GAUGE_NAME) instanceof Gauge);
        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.CONNECTION_FAILURES_METER_NAME) instanceof Meter);
    }
}
