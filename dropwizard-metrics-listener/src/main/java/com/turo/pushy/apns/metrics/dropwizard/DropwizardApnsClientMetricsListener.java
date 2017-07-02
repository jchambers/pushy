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

import com.codahale.metrics.*;
import com.turo.pushy.apns.ApnsClient;
import com.turo.pushy.apns.ApnsClientMetricsListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>An {@link ApnsClientMetricsListener} implementation that gathers and reports metrics
 * using the <a href="http://metrics.dropwizard.io/3.1.0/">Dropwizard Metrics library</a>. A
 * {@code DropwizardApnsClientMetricsListener} is intended to be used with a single
 * {@link ApnsClient} instance; to gather metrics from multiple clients, callers should create
 * multiple listeners.</p>
 *
 * <p>Note that a {@code DropwizardApnsClientMetricsListener} is, itself, a {@link com.codahale.metrics.MetricSet}, and
 * so it can be registered as a metric in another {@link com.codahale.metrics.MetricRegistry}. The metrics provided by a
 * {@code DropwizardApnsClientMetricsListener} are:</p>
 *
 * <dl>
 *  <dt>{@value DropwizardApnsClientMetricsListener#NOTIFICATION_TIMER_NAME}</dt>
 *  <dd>A {@link com.codahale.metrics.Timer} that measures the time between sending notifications and receiving a reply
 *  (whether accepted or rejected) from the APNs server.</dd>
 *
 *  <dt>{@value DropwizardApnsClientMetricsListener#WRITE_FAILURES_METER_NAME}</dt>
 *  <dd>A {@link com.codahale.metrics.Meter} that measures the number and rate of failures to send notifications to the
 *  APNs server.</dd>
 *
 *  <dt>{@value DropwizardApnsClientMetricsListener#SENT_NOTIFICATIONS_METER_NAME}</dt>
 *  <dd>A {@link com.codahale.metrics.Meter} that measures the number and rate of notifications successfully sent to the
 *  APNs server.</dd>
 *
 *  <dt>{@value DropwizardApnsClientMetricsListener#ACCEPTED_NOTIFICATIONS_METER_NAME}</dt>
 *  <dd>A {@link com.codahale.metrics.Meter} that measures the number and rate of notifications accepted by the APNs
 *  server.</dd>
 *
 *  <dt>{@value DropwizardApnsClientMetricsListener#REJECTED_NOTIFICATIONS_METER_NAME}</dt>
 *  <dd>A {@link com.codahale.metrics.Meter} that measures the number and rate of notifications rejected by the APNs
 *  server.</dd>
 *
 *  <dt>{@value DropwizardApnsClientMetricsListener#OPEN_CONNECTIONS_GAUGE_NAME}</dt>
 *  <dd>A {@link com.codahale.metrics.Gauge} that indicates number of open connections.</dd>
 *
 *  <dt>{@value DropwizardApnsClientMetricsListener#CONNECTION_FAILURES_METER_NAME}</dt>
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

    private final AtomicInteger openConnections = new AtomicInteger(0);
    private final Meter connectionFailures;

    /**
     * The name of a {@link com.codahale.metrics.Timer} that measures round-trip time when sending notifications.
     *
     * @see DropwizardApnsClientMetricsListener#getMetrics()
     */
    public static final String NOTIFICATION_TIMER_NAME = "notificationTimer";

    /**
     * The name of a {@link com.codahale.metrics.Meter} that measures write failure rates when sending notifications.
     *
     * @see DropwizardApnsClientMetricsListener#getMetrics()
     */
    public static final String WRITE_FAILURES_METER_NAME = "writeFailures";

    /**
     * The name of a {@link com.codahale.metrics.Meter} that measures write throughput when sending notifications.
     *
     * @see DropwizardApnsClientMetricsListener#getMetrics()
     */
    public static final String SENT_NOTIFICATIONS_METER_NAME = "sentNotifications";

    /**
     * The name of a {@link com.codahale.metrics.Meter} that measures notifications accepted by the APNs server.
     *
     * @see DropwizardApnsClientMetricsListener#getMetrics()
     */
    public static final String ACCEPTED_NOTIFICATIONS_METER_NAME = "acceptedNotifications";

    /**
     * The name of a {@link com.codahale.metrics.Meter} that measures notifications rejected by the APNs server.
     *
     * @see DropwizardApnsClientMetricsListener#getMetrics()
     */
    public static final String REJECTED_NOTIFICATIONS_METER_NAME = "rejectedNotifications";

    /**
     * The name of a {@link com.codahale.metrics.Gauge} that measures the number of open connections in an APNs client's
     * internal connection pool.
     *
     * @see DropwizardApnsClientMetricsListener#getMetrics()
     */
    public static final String OPEN_CONNECTIONS_GAUGE_NAME = "openConnections";

    /**
     * The name of a {@link com.codahale.metrics.Meter} that measures connection failure rates.
     *
     * @see DropwizardApnsClientMetricsListener#getMetrics()
     */
    public static final String CONNECTION_FAILURES_METER_NAME = "connectionFailures";

    /**
     * Constructs a new {@code ApnsClientMetricsListener} that gathers metrics with the Dropwizard Metrics library.
     */
    public DropwizardApnsClientMetricsListener() {
        this.metrics = new MetricRegistry();

        this.notificationTimer = this.metrics.timer(NOTIFICATION_TIMER_NAME);
        this.notificationTimerContexts = new HashMap<>();

        this.writeFailures = this.metrics.meter(WRITE_FAILURES_METER_NAME);
        this.sentNotifications = this.metrics.meter(SENT_NOTIFICATIONS_METER_NAME);
        this.acceptedNotifications = this.metrics.meter(ACCEPTED_NOTIFICATIONS_METER_NAME);
        this.rejectedNotifications = this.metrics.meter(REJECTED_NOTIFICATIONS_METER_NAME);

        this.metrics.register(OPEN_CONNECTIONS_GAUGE_NAME, new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return DropwizardApnsClientMetricsListener.this.openConnections.get();
            }
        });

        this.connectionFailures = this.metrics.meter(CONNECTION_FAILURES_METER_NAME);
    }

    /**
     * Records a failed attempt to send a notification and updates metrics accordingly.
     *
     * @param apnsClient the client that failed to write the notification; note that this is ignored by
     * {@code DropwizardApnsClientMetricsListener} instances, which should always be used for exactly one client
     * @param notificationId an opaque, unique identifier for the notification that could not be written
     */
    @Override
    public void handleWriteFailure(final ApnsClient apnsClient, final long notificationId) {
        this.stopTimerForNotification(notificationId);
        this.writeFailures.mark();
    }

    /**
     * Records a successful attempt to send a notification and updates metrics accordingly.
     *
     * @param apnsClient the client that sent the notification; note that this is ignored by
     * {@code DropwizardApnsClientMetricsListener} instances, which should always be used for exactly one client
     * @param notificationId an opaque, unique identifier for the notification that was sent
     */
    @Override
    public void handleNotificationSent(final ApnsClient apnsClient, final long notificationId) {
        this.sentNotifications.mark();
        this.notificationTimerContexts.put(notificationId, this.notificationTimer.time());
    }

    /**
     * Records that the APNs server accepted a previously-sent notification and updates metrics accordingly.
     *
     * @param apnsClient the client that sent the accepted notification; note that this is ignored by
     * {@code DropwizardApnsClientMetricsListener} instances, which should always be used for exactly one client
     * @param notificationId an opaque, unique identifier for the notification that was accepted
     */
    @Override
    public void handleNotificationAccepted(final ApnsClient apnsClient, final long notificationId) {
        this.stopTimerForNotification(notificationId);
        this.acceptedNotifications.mark();
    }

    /**
     * Records that the APNs server rejected a previously-sent notification and updates metrics accordingly.
     *
     * @param apnsClient the client that sent the rejected notification; note that this is ignored by
     * {@code DropwizardApnsClientMetricsListener} instances, which should always be used for exactly one client
     * @param notificationId an opaque, unique identifier for the notification that was rejected
     */
    @Override
    public void handleNotificationRejected(final ApnsClient apnsClient, final long notificationId) {
        this.stopTimerForNotification(notificationId);
        this.rejectedNotifications.mark();
    }

    /**
     * Records that the APNs server added a new connection to its internal connection pool and updates metrics
     * accordingly.
     *
     * @param apnsClient the client that added the new connection
     */
    @Override
    public void handleConnectionAdded(final ApnsClient apnsClient) {
        this.openConnections.incrementAndGet();
    }

    /**
     * Records that the APNs server removed a connection from its internal connection pool and updates metrics
     * accordingly.
     *
     * @param apnsClient the client that removed the connection
     */
    @Override
    public void handleConnectionRemoved(final ApnsClient apnsClient) {
        this.openConnections.decrementAndGet();
    }

    /**
     * Records that a previously-started attempt to connect to the APNs server failed and updates metrics accordingly.
     *
     * @param apnsClient the client that failed to connect; note that this is ignored by
     * {@code DropwizardApnsClientMetricsListener} instances, which should always be used for exactly one client
     */
    @Override
    public void handleConnectionCreationFailed(final ApnsClient apnsClient) {
        this.connectionFailures.mark();
    }

    private void stopTimerForNotification(final long notificationId) {
        final Timer.Context timerContext = this.notificationTimerContexts.remove(notificationId);

        if (timerContext != null) {
            timerContext.stop();
        }
    }

    /**
     * Returns the metrics produced by this listener.
     *
     * @return a map of metric names to metrics
     *
     * @see DropwizardApnsClientMetricsListener#NOTIFICATION_TIMER_NAME
     * @see DropwizardApnsClientMetricsListener#WRITE_FAILURES_METER_NAME
     * @see DropwizardApnsClientMetricsListener#SENT_NOTIFICATIONS_METER_NAME
     * @see DropwizardApnsClientMetricsListener#ACCEPTED_NOTIFICATIONS_METER_NAME
     * @see DropwizardApnsClientMetricsListener#REJECTED_NOTIFICATIONS_METER_NAME
     * @see DropwizardApnsClientMetricsListener#OPEN_CONNECTIONS_GAUGE_NAME
     * @see DropwizardApnsClientMetricsListener#CONNECTION_FAILURES_METER_NAME
     */
    @Override
    public Map<String, Metric> getMetrics() {
        return this.metrics.getMetrics();
    }
}
