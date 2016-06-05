package com.relayrides.pushy.apns.metrics.dropwizard;

import java.util.HashMap;
import java.util.Map;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;
import com.relayrides.pushy.apns.ApnsClient;
import com.relayrides.pushy.apns.ApnsClientMetricsListener;
import com.relayrides.pushy.apns.ApnsPushNotification;

/**
 * <p>An {@link com.relayrides.pushy.apns.ApnsClientMetricsListener} implementation that gathers and reports metrics
 * using the <a href="http://metrics.dropwizard.io/3.1.0/">Dropwizard Metrics library</a>. A
 * {@code DropwizardApnsClientMetricsListener} is intended to be used with a single
 * {@link com.relayrides.pushy.apns.ApnsClient} instance; to gather metrics from multiple clients, callers should create
 * multiple listeners.</p>
 *
 * <p>Note that a {@code DropwizardApnsClientMetricsListener} is, itself, a {@link com.codahale.metrics.MetricSet}, and
 * so it can be registered as a metric in another {@link com.codahale.metrics.MetricRegistry}. The metrics provided by a
 * {@code DropwizardApnsClientMetricsListener} are:</p>
 *
 * <dl>
 *  <dt>{@value com.relayrides.pushy.apns.metrics.dropwizard.DropwizardApnsClientMetricsListener#NOTIFICATION_TIMER_NAME}</dt>
 *  <dd>A {@link com.codahale.metrics.Timer} that measures the time between sending notifications and receiving a reply
 *  (whether accepted or rejected) from the APNs server.</dd>
 *
 *  <dt>{@value com.relayrides.pushy.apns.metrics.dropwizard.DropwizardApnsClientMetricsListener#WRITE_FAILURES_METER_NAME}</dt>
 *  <dd>A {@link com.codahale.metrics.Meter} that measures the number and rate of failures to send notifications to the
 *  APNs server.</dd>
 *
 *  <dt>{@value com.relayrides.pushy.apns.metrics.dropwizard.DropwizardApnsClientMetricsListener#SENT_NOTIFICATIONS_METER_NAME}</dt>
 *  <dd>A {@link com.codahale.metrics.Meter} that measures the number and rate of notifications successfully sent to the
 *  APNs server.</dd>
 *
 *  <dt>{@value com.relayrides.pushy.apns.metrics.dropwizard.DropwizardApnsClientMetricsListener#ACCEPTED_NOTIFICATIONS_METER_NAME}</dt>
 *  <dd>A {@link com.codahale.metrics.Meter} that measures the number and rate of notifications accepted by the APNs
 *  server.</dd>
 *
 *  <dt>{@value com.relayrides.pushy.apns.metrics.dropwizard.DropwizardApnsClientMetricsListener#REJECTED_NOTIFICATIONS_METER_NAME}</dt>
 *  <dd>A {@link com.codahale.metrics.Meter} that measures the number and rate of notifications rejected by the APNs
 *  server.</dd>
 *
 *  <dt>{@value com.relayrides.pushy.apns.metrics.dropwizard.DropwizardApnsClientMetricsListener#CONNECTION_GAUGE_NAME}</dt>
 *  <dd>A {@link com.codahale.metrics.Gauge} that indicates whether the monitored client is currently connected to the
 *  APNs server.</dd>
 *
 *  <dt>{@value com.relayrides.pushy.apns.metrics.dropwizard.DropwizardApnsClientMetricsListener#CONNECTION_TIMER_NAME}</dt>
 *  <dd>A {@link com.codahale.metrics.Timer} that measures the time taken to establish connections to the APNs
 *  server.</dd>
 *
 *  <dt>{@value com.relayrides.pushy.apns.metrics.dropwizard.DropwizardApnsClientMetricsListener#CONNECTION_FAILURES_METER_NAME}</dt>
 *  <dd>A {@link com.codahale.metrics.Meter} that measures the number and rate of failed attempts to connect to the APNs
 *  server.</dd>
 * </dl>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public class DropwizardApnsClientMetricsListener implements ApnsClientMetricsListener, MetricSet {

    private final MetricRegistry metrics;

    private final Timer notificationTimer;
    private final Map<Long, Timer.Context> notificationTimerContexts;

    private final Meter writeFailures;
    private final Meter sentNotifications;
    private final Meter acceptedNotifications;
    private final Meter rejectedNotifications;

    private boolean connected;
    private final Timer connectionTimer;
    private Timer.Context connectionTimerContext;

    private final Meter connectionFailures;

    public static final String NOTIFICATION_TIMER_NAME = "notificationTimer";

    public static final String WRITE_FAILURES_METER_NAME = "writeFailures";
    public static final String SENT_NOTIFICATIONS_METER_NAME = "sentNotifications";
    public static final String ACCEPTED_NOTIFICATIONS_METER_NAME = "acceptedNotifications";
    public static final String REJECTED_NOTIFICATIONS_METER_NAME = "rejectedNotifications";

    public static final String CONNECTION_GAUGE_NAME = "connected";
    public static final String CONNECTION_TIMER_NAME = "connectionTimer";
    public static final String CONNECTION_FAILURES_METER_NAME = "connectionFailures";

    /**
     * Constructs a new {@code ApnsClientMetricsListener} that gathers metrics with the Dropwizard Metrics library.
     */
    public DropwizardApnsClientMetricsListener() {
        this.metrics = new MetricRegistry();

        this.notificationTimer = this.metrics.timer(NOTIFICATION_TIMER_NAME);
        this.notificationTimerContexts = new HashMap<Long, Timer.Context>();

        this.writeFailures = this.metrics.meter(WRITE_FAILURES_METER_NAME);
        this.sentNotifications = this.metrics.meter(SENT_NOTIFICATIONS_METER_NAME);
        this.acceptedNotifications = this.metrics.meter(ACCEPTED_NOTIFICATIONS_METER_NAME);
        this.rejectedNotifications = this.metrics.meter(REJECTED_NOTIFICATIONS_METER_NAME);

        this.metrics.register(CONNECTION_GAUGE_NAME, new Gauge<Boolean>() {

            @Override
            public Boolean getValue() {
                return DropwizardApnsClientMetricsListener.this.connected;
            }
        });

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
        this.connected = false;
    }

    @Override
    public void handleConnectionAttemptSucceeded(final ApnsClient<? extends ApnsPushNotification> apnsClient) {
        this.stopConnectionTimer();
        this.connected = true;
    }

    @Override
    public void handleConnectionAttemptFailed(final ApnsClient<? extends ApnsPushNotification> apnsClient) {
        this.stopConnectionTimer();
        this.connectionFailures.mark();
        this.connected = false;
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
