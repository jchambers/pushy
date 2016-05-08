package com.relayrides.pushy.apns.metrics.dropwizard;

import java.util.HashMap;
import java.util.Map;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;
import com.relayrides.pushy.apns.ApnsClient;
import com.relayrides.pushy.apns.ApnsClientMetricsListener;
import com.relayrides.pushy.apns.ApnsPushNotification;

public class DropwizardApnsClientMetricsListener implements ApnsClientMetricsListener, MetricSet {

    private final MetricRegistry metrics;

    private final Timer notificationTimer;
    private final Map<Long, Timer.Context> notificationTimerContexts;

    private final Meter writeFailures;
    private final Meter sentNotifications;
    private final Meter acceptedNotifications;
    private final Meter rejectedNotifications;

    private final Timer connectionTimer;
    private Timer.Context connectionTimerContext;

    private final Meter connectionFailures;

    public static final String NOTIFICATION_TIMER_NAME = "notificationTimer";

    public static final String WRITE_FAILURES_METER_NAME = "writeFailures";
    public static final String SENT_NOTIFICATIONS_METER_NAME = "sentNotifications";
    public static final String ACCEPTED_NOTIFICATIONS_METER_NAME = "acceptedNotifications";
    public static final String REJECTED_NOTIFICATIONS_METER_NAME = "rejectedNotifications";

    public static final String CONNECTION_TIMER_NAME = "connectionTimer";
    public static final String CONNECTION_FAILURES_METER_NAME = "connectionFailures";

    public DropwizardApnsClientMetricsListener() {
        this.metrics = new MetricRegistry();

        this.notificationTimer = this.metrics.timer(NOTIFICATION_TIMER_NAME);
        this.notificationTimerContexts = new HashMap<Long, Timer.Context>();

        this.writeFailures = this.metrics.meter(WRITE_FAILURES_METER_NAME);
        this.sentNotifications = this.metrics.meter(SENT_NOTIFICATIONS_METER_NAME);
        this.acceptedNotifications = this.metrics.meter(ACCEPTED_NOTIFICATIONS_METER_NAME);
        this.rejectedNotifications = this.metrics.meter(REJECTED_NOTIFICATIONS_METER_NAME);

        this.connectionTimer = this.metrics.timer(CONNECTION_TIMER_NAME);
        this.connectionFailures = this.metrics.meter(CONNECTION_FAILURES_METER_NAME);
    }

    @Override
    public void handleWriteFailure(final ApnsClient<? extends ApnsPushNotification> apnsClient, final long notificationId) {
        this.stopTimerForNotification(notificationId);
        this.writeFailures.mark();
    }

    @Override
    public void handleNotificationSent(final ApnsClient<? extends ApnsPushNotification> apnsClient, final long notificationId) {
        this.sentNotifications.mark();
        this.notificationTimerContexts.put(notificationId, this.notificationTimer.time());
    }

    @Override
    public void handleNotificationAccepted(final ApnsClient<? extends ApnsPushNotification> apnsClient, final long notificationId) {
        this.stopTimerForNotification(notificationId);
        this.acceptedNotifications.mark();
    }

    @Override
    public void handleNotificationRejected(final ApnsClient<? extends ApnsPushNotification> apnsClient, final long notificationId) {
        this.stopTimerForNotification(notificationId);
        this.rejectedNotifications.mark();
    }

    private void stopTimerForNotification(final long notificationId) {
        final Timer.Context timerContext = this.notificationTimerContexts.remove(notificationId);

        if (timerContext != null) {
            timerContext.stop();
        }
    }

    @Override
    public void handleConnectionAttemptStarted(final ApnsClient<? extends ApnsPushNotification> apnsClient) {
        this.connectionTimerContext = this.connectionTimer.time();
    }

    @Override
    public void handleConnectionAttemptSucceeded(final ApnsClient<? extends ApnsPushNotification> apnsClient) {
        this.stopConnectionTimer();
    }

    @Override
    public void handleConnectionAttemptFailed(final ApnsClient<? extends ApnsPushNotification> apnsClient) {
        this.stopConnectionTimer();
        this.connectionFailures.mark();
    }

    private void stopConnectionTimer() {
        if (this.connectionTimerContext != null) {
            this.connectionTimerContext.stop();
        }
    }

    @Override
    public Map<String, Metric> getMetrics() {
        return this.metrics.getMetrics();
    }
}
