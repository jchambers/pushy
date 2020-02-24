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

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientMetricsListener;
import io.micrometer.core.instrument.*;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>An {@link ApnsClientMetricsListener} implementation that gathers and reports metrics
 * using the <a href="http://micrometer.io/">Micrometer application monitoring library</a>. A
 * {@code MicrometerApnsClientMetricsListener} is intended to be used with a single
 * {@link ApnsClient} instance; to gather metrics from multiple clients, callers should create
 * multiple listeners.</p>
 *
 * <p>Callers provide a {@link io.micrometer.core.instrument.MeterRegistry} to the
 * {@code MicrometerApnsClientMetricsListener} at construction time, and the listener populates the registry with the
 * following metrics:</p>
 *
 * <dl>
 *  <dt>{@value #NOTIFICATION_TIMER_NAME}</dt>
 *  <dd>A {@link io.micrometer.core.instrument.Timer} that measures the time between sending notifications and receiving
 *  a reply (whether accepted or rejected) from the APNs server.</dd>
 *
 *  <dt>{@value #WRITE_FAILURES_COUNTER_NAME}</dt>
 *  <dd>A {@link io.micrometer.core.instrument.Counter} that measures the number and rate of failures to send notifications to the
 *  APNs server.</dd>
 *
 *  <dt>{@value #SENT_NOTIFICATIONS_COUNTER_NAME}</dt>
 *  <dd>A {@link io.micrometer.core.instrument.Counter} that measures the number and rate of notifications successfully sent to the
 *  APNs server.</dd>
 *
 *  <dt>{@value #ACCEPTED_NOTIFICATIONS_COUNTER_NAME}</dt>
 *  <dd>A {@link io.micrometer.core.instrument.Counter} that measures the number and rate of notifications accepted by the APNs
 *  server.</dd>
 *
 *  <dt>{@value #REJECTED_NOTIFICATIONS_COUNTER_NAME}</dt>
 *  <dd>A {@link io.micrometer.core.instrument.Counter} that measures the number and rate of notifications rejected by the APNs
 *  server.</dd>
 *
 *  <dt>{@value #OPEN_CONNECTIONS_GAUGE_NAME}</dt>
 *  <dd>A {@link io.micrometer.core.instrument.Gauge} that indicates number of open connections.</dd>
 *
 *  <dt>{@value #CONNECTION_FAILURES_COUNTER_NAME}</dt>
 *  <dd>A {@link io.micrometer.core.instrument.Counter} that measures the number and rate of failed attempts to connect to the APNs
 *  server.</dd>
 * </dl>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public class MicrometerApnsClientMetricsListener implements ApnsClientMetricsListener {

    private final Timer notificationTimer;
    private final ConcurrentMap<Long, Long> notificationStartTimes;

    private final Counter writeFailures;
    private final Counter sentNotifications;
    private final Counter acceptedNotifications;
    private final Counter rejectedNotifications;

    private final AtomicInteger openConnections = new AtomicInteger(0);
    private final Counter connectionFailures;

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    /**
     * The name of a {@link io.micrometer.core.instrument.Timer} that measures round-trip time when sending
     * notifications.
     */
    public static final String NOTIFICATION_TIMER_NAME = "notifications.sent.timer";

    /**
     * The name of a {@link io.micrometer.core.instrument.Counter} that measures the number of write failures when
     * sending notifications.
     */
    public static final String WRITE_FAILURES_COUNTER_NAME = "notifications.failed";

    /**
     * The name of a {@link io.micrometer.core.instrument.Counter} that measures the number of notifications sent
     * (regardless of whether they're accepted or rejected by the server).
     */
    public static final String SENT_NOTIFICATIONS_COUNTER_NAME = "notifications.sent";

    /**
     * The name of a {@link io.micrometer.core.instrument.Counter} that measures the number of notifications accepted by
     * the APNs server.
     */
    public static final String ACCEPTED_NOTIFICATIONS_COUNTER_NAME = "notifications.accepted";

    /**
     * The name of a {@link io.micrometer.core.instrument.Counter} that measures the number of notifications rejected by
     * the APNs server.
     */
    public static final String REJECTED_NOTIFICATIONS_COUNTER_NAME = "notifications.rejected";

    /**
     * The name of a {@link io.micrometer.core.instrument.Gauge} that measures the number of open connections in an APNs
     * client's internal connection pool.
     */
    public static final String OPEN_CONNECTIONS_GAUGE_NAME = "connections.open";

    /**
     * The name of a {@link io.micrometer.core.instrument.Counter} that measures the number of a client's failed
     * connection attempts.
     */
    public static final String CONNECTION_FAILURES_COUNTER_NAME = "connections.failed";

    /**
     * Constructs a new Micrometer metrics listener that adds metrics to the given registry with the given list of tags.
     *
     * @param meterRegistry the registry to which to add metrics
     * @param tags an optional list of tags to attach to metrics; may be {@code null} or empty, in which case no tags
     * are added
     */
    public MicrometerApnsClientMetricsListener(final MeterRegistry meterRegistry, final List<String> tags) {
        this(meterRegistry, tags != null ? tags.toArray(EMPTY_STRING_ARRAY) : null);
    }

    /**
     * Constructs a new Micrometer metrics listener that adds metrics to the given registry with the given list of tags.
     *
     * @param meterRegistry the registry to which to add metrics
     * @param tagKeysAndValues an optional list of tag keys/values to attach to metrics; must be an even number of
     * strings representing alternating key/value pairs
     */
    public MicrometerApnsClientMetricsListener(final MeterRegistry meterRegistry, final String... tagKeysAndValues) {
        this(meterRegistry, Tags.of(tagKeysAndValues));
    }

    /**
     * Constructs a new Micrometer metrics listener that adds metrics to the given registry with the given list of tags.
     *
     * @param meterRegistry the registry to which to add metrics
     * @param tags an optional collection of tags to attach to metrics; may be empty
     */
    public MicrometerApnsClientMetricsListener(final MeterRegistry meterRegistry, final Iterable<Tag> tags) {
        this.notificationStartTimes = new ConcurrentHashMap<>();
        this.notificationTimer = meterRegistry.timer(NOTIFICATION_TIMER_NAME, tags);

        this.writeFailures = meterRegistry.counter(WRITE_FAILURES_COUNTER_NAME, tags);
        this.sentNotifications = meterRegistry.counter(SENT_NOTIFICATIONS_COUNTER_NAME, tags);
        this.acceptedNotifications = meterRegistry.counter(ACCEPTED_NOTIFICATIONS_COUNTER_NAME, tags);
        this.rejectedNotifications = meterRegistry.counter(REJECTED_NOTIFICATIONS_COUNTER_NAME, tags);

        this.connectionFailures = meterRegistry.counter(CONNECTION_FAILURES_COUNTER_NAME, tags);

        meterRegistry.gauge(OPEN_CONNECTIONS_GAUGE_NAME, tags, openConnections);
    }

    /**
     * Records a failed attempt to send a notification and updates metrics accordingly.
     *
     * @param apnsClient the client that failed to write the notification; note that this is ignored by
     * {@code MicrometerApnsClientMetricsListener} instances, which should always be used for exactly one client
     * @param notificationId an opaque, unique identifier for the notification that could not be written
     */
    @Override
    public void handleWriteFailure(final ApnsClient apnsClient, final long notificationId) {
        this.notificationStartTimes.remove(notificationId);
        this.writeFailures.increment();
    }

    /**
     * Records a successful attempt to send a notification and updates metrics accordingly.
     *
     * @param apnsClient the client that sent the notification; note that this is ignored by
     * {@code MicrometerApnsClientMetricsListener} instances, which should always be used for exactly one client
     * @param notificationId an opaque, unique identifier for the notification that was sent
     */
    @Override
    public void handleNotificationSent(final ApnsClient apnsClient, final long notificationId) {
        this.notificationStartTimes.put(notificationId, System.nanoTime());
        this.sentNotifications.increment();
    }

    /**
     * Records that the APNs server accepted a previously-sent notification and updates metrics accordingly.
     *
     * @param apnsClient the client that sent the accepted notification; note that this is ignored by
     * {@code MicrometerApnsClientMetricsListener} instances, which should always be used for exactly one client
     * @param notificationId an opaque, unique identifier for the notification that was accepted
     */
    @Override
    public void handleNotificationAccepted(final ApnsClient apnsClient, final long notificationId) {
        this.recordEndTimeForNotification(notificationId);
        this.acceptedNotifications.increment();
    }

    /**
     * Records that the APNs server rejected a previously-sent notification and updates metrics accordingly.
     *
     * @param apnsClient the client that sent the rejected notification; note that this is ignored by
     * {@code MicrometerApnsClientMetricsListener} instances, which should always be used for exactly one client
     * @param notificationId an opaque, unique identifier for the notification that was rejected
     */
    @Override
    public void handleNotificationRejected(final ApnsClient apnsClient, final long notificationId) {
        this.recordEndTimeForNotification(notificationId);
        this.rejectedNotifications.increment();
    }

    private void recordEndTimeForNotification(final long notificationId) {
        final long endTime = System.nanoTime();
        final Long startTime = this.notificationStartTimes.remove(notificationId);

        if (startTime != null) {
            this.notificationTimer.record(endTime - startTime, TimeUnit.NANOSECONDS);
        }
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
     * {@code MicrometerApnsClientMetricsListener} instances, which should always be used for exactly one client
     */
    @Override
    public void handleConnectionCreationFailed(final ApnsClient apnsClient) {
        this.connectionFailures.increment();
    }
}
