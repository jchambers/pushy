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
import com.eatthepath.pushy.apns.PushNotificationResponse;
import io.micrometer.core.instrument.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>An {@link ApnsClientMetricsListener} implementation that gathers and reports metrics using the
 * <a href="http://micrometer.io/">Micrometer application monitoring library</a>. A
 * {@code MicrometerApnsClientMetricsListener} is intended to be used with a single {@link ApnsClient} instance; to
 * gather metrics from multiple clients, callers should create multiple listeners and should consider providing separate
 * sets of identifying tags to each listener.</p>
 *
 * <p>Callers provide a {@link io.micrometer.core.instrument.MeterRegistry} to the
 * {@code MicrometerApnsClientMetricsListener} at construction time, and the listener populates the registry with the
 * following metrics:</p>
 *
 * <dl>
 *  <dt>{@value #WRITE_FAILURES_COUNTER_NAME}</dt>
 *  <dd>A {@link io.micrometer.core.instrument.Counter} that measures the number of failures to send notifications to
 *  the APNs server. In addition to the tags provided at listener construction time, this counter will also be tagged
 *  with:
 *
 *  <dl>
 *    <dt>{@value #TOPIC_TAG_NAME}</dt>
 *    <dd>The APNs topic to which each notification was sent</dd>
 *  </dl>
 *  </dd>
 *
 *  <dt>{@value #SENT_NOTIFICATIONS_COUNTER_NAME}</dt>
 *  <dd>A {@link io.micrometer.core.instrument.Counter} that measures the number of notifications successfully sent to
 *  the APNs server. In addition to the tags provided at listener construction time, this counter will also be tagged
 *  with:
 *
 *  <dl>
 *    <dt>{@value #TOPIC_TAG_NAME}</dt>
 *    <dd>The APNs topic to which each notification was sent</dd>
 *  </dl>
 *  </dd>
 *
 *  <dt>{@value #ACKNOWLEDGED_NOTIFICATIONS_TIMER_NAME}</dt>
 *  <dd>A {@link io.micrometer.core.instrument.Timer} that measures the time between sending notifications and receiving
 *  a reply (whether accepted or rejected) from the APNs server. In addition to the tags provided at listener
 *  construction time, this counter will also be tagged with:
 *
 *  <dl>
 *    <dt>{@value #TOPIC_TAG_NAME}</dt>
 *    <dd>The APNs topic to which each notification was sent</dd>
 *
 *    <dt>{@value #ACCEPTED_TAG_NAME}</dt>
 *    <dd>"true" if the notification was accepted by the APNs server or "false" if the notification was rejected</dd>
 *
 *    <dt>{@value #STATUS_TAG_NAME}</dt>
 *    <dd>The HTTP status code returned by the APNs server</dd>
 *
 *    <dt>{@value REASON_TAG_NAME}</dt>
 *    <dd>The rejection reason provided by the APNs server if the notification was rejected; not set if the notification
 *    was accepted</dd>
 *  </dl>
 *  </dd>
 *
 *  <dt>{@value #OPEN_CONNECTIONS_GAUGE_NAME}</dt>
 *  <dd>A {@link io.micrometer.core.instrument.Gauge} that indicates number of open connections.</dd>
 *
 *  <dt>{@value #CONNECTION_FAILURES_COUNTER_NAME}</dt>
 *  <dd>A {@link io.micrometer.core.instrument.Counter} that measures the number of failed attempts to connect to the
 *  APNs server.</dd>
 * </dl>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public class MicrometerApnsClientMetricsListener implements ApnsClientMetricsListener {

    private final MeterRegistry meterRegistry;
    private final Tags tags;

    private final AtomicInteger openConnections = new AtomicInteger(0);
    private final Counter connectionFailures;

    /**
     * The name of a {@link io.micrometer.core.instrument.Counter} that measures the number of write failures when
     * sending notifications.
     */
    public static final String WRITE_FAILURES_COUNTER_NAME = "pushy.notifications.failed";

    /**
     * The name of a {@link io.micrometer.core.instrument.Counter} that measures the number of notifications sent
     * (regardless of whether they're accepted or rejected by the server).
     */
    public static final String SENT_NOTIFICATIONS_COUNTER_NAME = "pushy.notifications.sent";

    /**
     * The name of a {@link io.micrometer.core.instrument.Timer} that measures round-trip time when sending
     * notifications.
     */
    public static final String ACKNOWLEDGED_NOTIFICATIONS_TIMER_NAME = "pushy.notifications.acknowledged";

    /**
     * The name of a {@link io.micrometer.core.instrument.Gauge} that measures the number of open connections in an APNs
     * client's internal connection pool.
     */
    public static final String OPEN_CONNECTIONS_GAUGE_NAME = "pushy.connections.open";

    /**
     * The name of a {@link io.micrometer.core.instrument.Counter} that measures the number of a client's failed
     * connection attempts.
     */
    public static final String CONNECTION_FAILURES_COUNTER_NAME = "pushy.connections.failed";

    /**
     * The name of a tag attached to most metrics that indicates the APNs topic to which a notification was sent.
     */
    public static final String TOPIC_TAG_NAME = "topic";

    /**
     * The name of a tag attached to the {@value #ACKNOWLEDGED_NOTIFICATIONS_TIMER_NAME} timer indicating if a
     * notification was accepted by the APNs server.
     */
    public static final String ACCEPTED_TAG_NAME = "accepted";

    /**
     * The name of a tag attached to the {@value #ACKNOWLEDGED_NOTIFICATIONS_TIMER_NAME} timer indicating the reason why
     * a notification was rejected by the APNs server.
     */
    public static final String REASON_TAG_NAME = "reason";

    /**
     * The name of a tag attached to the {@value #ACKNOWLEDGED_NOTIFICATIONS_TIMER_NAME} timer indicating the HTTP
     * status code reported by the APNs server.
     */
    public static final String STATUS_TAG_NAME = "status";

    /**
     * Constructs a new Micrometer metrics listener that adds metrics to the given registry with the given list of tags.
     *
     * @param meterRegistry the registry to which to add metrics
     * @param tagKeysAndValues an optional list of tag keys/values to attach to all metrics produced by this listener;
     * must be an even number of strings representing alternating key/value pairs
     */
    public MicrometerApnsClientMetricsListener(final MeterRegistry meterRegistry, final String... tagKeysAndValues) {
        this(meterRegistry, Tags.of(tagKeysAndValues));
    }

    /**
     * Constructs a new Micrometer metrics listener that adds metrics to the given registry with the given list of tags.
     *
     * @param meterRegistry the registry to which to add metrics
     * @param tags an optional collection of tags to attach to all metrics produced by this listener; may be empty
     * or {@code null}
     */
    public MicrometerApnsClientMetricsListener(final MeterRegistry meterRegistry, final Iterable<Tag> tags) {
        this.meterRegistry = meterRegistry;
        this.tags = Tags.of(tags);

        this.connectionFailures = meterRegistry.counter(CONNECTION_FAILURES_COUNTER_NAME, this.tags);
        meterRegistry.gauge(OPEN_CONNECTIONS_GAUGE_NAME, this.tags, openConnections);
    }

    /**
     * Records a failed attempt to send a notification and updates metrics accordingly.
     *
     * @param topic the APNs topic to which the notification was sent
     */
    @Override
    public void handleWriteFailure(final String topic) {
        this.meterRegistry.counter(WRITE_FAILURES_COUNTER_NAME, this.tags.and(TOPIC_TAG_NAME, topic)).increment();
    }

    /**
     * Records a successful attempt to send a notification and updates metrics accordingly.
     *
     * @param topic the APNs topic to which the notification was sent
     */
    @Override
    public void handleNotificationSent(final String topic) {
        this.meterRegistry.counter(SENT_NOTIFICATIONS_COUNTER_NAME, this.tags.and(TOPIC_TAG_NAME, topic)).increment();
    }

    /**
     * Records that the APNs server accepted or rejected a previously-sent notification and updates metrics accordingly.
     *
     * @param response the response from the APNs server
     * @param durationNanos the duration, in nanoseconds, between the time the notification was initially sent and when
     * it was acknowledged by the APNs server
     */
    @Override
    public void handleNotificationAcknowledged(final PushNotificationResponse<?> response, final long durationNanos) {
        Tags tags = this.tags.and(
            TOPIC_TAG_NAME, response.getPushNotification().getTopic(),
            ACCEPTED_TAG_NAME, String.valueOf(response.isAccepted()),
            STATUS_TAG_NAME, String.valueOf(response.getStatusCode())
        );

        if (!response.isAccepted()) {
            tags = tags.and(REASON_TAG_NAME, response.getRejectionReason().orElse("unknown"));
        }

        this.meterRegistry.timer(ACKNOWLEDGED_NOTIFICATIONS_TIMER_NAME, tags).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Records that the APNs server added a new connection to its internal connection pool and updates metrics
     * accordingly.
     */
    @Override
    public void handleConnectionAdded() {
        this.openConnections.incrementAndGet();
    }

    /**
     * Records that the APNs server removed a connection from its internal connection pool and updates metrics
     * accordingly.
     */
    @Override
    public void handleConnectionRemoved() {
        this.openConnections.decrementAndGet();
    }

    /**
     * Records that a previously-started attempt to connect to the APNs server failed and updates metrics accordingly.
     */
    @Override
    public void handleConnectionCreationFailed() {
        this.connectionFailures.increment();
    }
}
