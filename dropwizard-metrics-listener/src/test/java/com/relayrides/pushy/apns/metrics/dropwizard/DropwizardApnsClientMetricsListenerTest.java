package com.relayrides.pushy.apns.metrics.dropwizard;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;

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
    public void testHandleConnectionAttemptStarted() {
        @SuppressWarnings("unchecked")
        final Gauge<Boolean> connectionGauge = (Gauge<Boolean>) this.listener.getMetrics().get(DropwizardApnsClientMetricsListener.CONNECTION_GAUGE_NAME);

        this.listener.handleConnectionAttemptStarted(null);
        assertFalse(connectionGauge.getValue());
    }

    @Test
    public void testHandleConnectionAttemptSucceeded() {
        @SuppressWarnings("unchecked")
        final Gauge<Boolean> connectionGauge = (Gauge<Boolean>) this.listener.getMetrics().get(DropwizardApnsClientMetricsListener.CONNECTION_GAUGE_NAME);
        final Timer connectionTimer = (Timer) this.listener.getMetrics().get(DropwizardApnsClientMetricsListener.CONNECTION_TIMER_NAME);
        assertEquals(0, connectionTimer.getCount());

        this.listener.handleConnectionAttemptStarted(null);
        assertFalse(connectionGauge.getValue());
        assertEquals(0, connectionTimer.getCount());

        this.listener.handleConnectionAttemptSucceeded(null);
        assertTrue(connectionGauge.getValue());
        assertEquals(1, connectionTimer.getCount());
    }

    @Test
    public void testHandleConnectionAttemptFailed() {
        @SuppressWarnings("unchecked")
        final Gauge<Boolean> connectionGauge = (Gauge<Boolean>) this.listener.getMetrics().get(DropwizardApnsClientMetricsListener.CONNECTION_GAUGE_NAME);
        final Meter connectionFailures = (Meter) this.listener.getMetrics().get(DropwizardApnsClientMetricsListener.CONNECTION_FAILURES_METER_NAME);
        final Timer connectionTimer = (Timer) this.listener.getMetrics().get(DropwizardApnsClientMetricsListener.CONNECTION_TIMER_NAME);
        assertEquals(0, connectionFailures.getCount());
        assertEquals(0, connectionTimer.getCount());

        this.listener.handleConnectionAttemptStarted(null);
        assertFalse(connectionGauge.getValue());
        assertEquals(0, connectionFailures.getCount());
        assertEquals(0, connectionTimer.getCount());

        this.listener.handleConnectionAttemptFailed(null);
        assertFalse(connectionGauge.getValue());
        assertEquals(1, connectionFailures.getCount());
        assertEquals(1, connectionTimer.getCount());
    }

    @Test
    public void testGetMetrics() {
        final Map<String, Metric> metrics = this.listener.getMetrics();

        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.NOTIFICATION_TIMER_NAME) instanceof Timer);

        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.WRITE_FAILURES_METER_NAME) instanceof Meter);
        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.SENT_NOTIFICATIONS_METER_NAME) instanceof Meter);
        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.ACCEPTED_NOTIFICATIONS_METER_NAME) instanceof Meter);
        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.REJECTED_NOTIFICATIONS_METER_NAME) instanceof Meter);

        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.CONNECTION_GAUGE_NAME) instanceof Gauge);
        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.CONNECTION_TIMER_NAME) instanceof Timer);
        assertTrue(metrics.get(DropwizardApnsClientMetricsListener.CONNECTION_FAILURES_METER_NAME) instanceof Meter);
    }
}
