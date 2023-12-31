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

package com.eatthepath.pushy.apns;

/**
 * <p>A metrics listener receives events from an {@link ApnsClient} that can be used to measure the performance and
 * behavior of the client. Although the information sent to a metrics listener is generally available by other means,
 * it is provided to listeners in a simplified form for ease of consumption and aggregation.</p>
 *
 * <p>The information provided to metrics listeners is intended only to measure the performance and behavior of an
 * {@code ApnsClient}; metrics listeners should never be used to drive business logic.</p>
 *
 * <p>Implementations of the {@code ApnsClientMetricsListener} interface should expect to receive calls from multiple
 * threads, and should take appropriate measures to ensure thread-safety.</p>
 *
 * @see ApnsClientBuilder#setMetricsListener(ApnsClientMetricsListener)
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.6
 */
public interface ApnsClientMetricsListener {

    /**
     * Indicates that an attempt to send a push notification failed before the notification was acknowledged by the APNs
     * server. Write failures may be the first event in a sequence for a given notification (indicating that the
     * notification was never written to the wire), but may also occur after a notification was sent if the connection
     * closed before the notification was acknowledged by the server.
     *
     * @param topic the APNs topic to which the notification was sent
     *
     * @since 0.16
     */
    void handleWriteFailure(String topic);

    /**
     * Indicates that a notification was sent to the APNs server. Note that a sent notification may still be either
     * accepted or rejected by the APNs server later; sending the notification doesn't imply anything about the ultimate
     * state of the notification.
     *
     * @param topic the APNs topic to which the notification was sent
     *
     * @since 0.16
     */
    void handleNotificationSent(String topic);

    /**
     * Indicates that a notification that was previously sent to an APNs server was acknowledged (i.e. either accepted
     * or rejected) by the server.
     *
     * @param response the response from the server
     * @param durationNanos the duration, measured in nanoseconds, between the time when
     * {@link ApnsClient#sendNotification(ApnsPushNotification)} was called for the notification in question and when
     * the server acknowledged the notification
     *
     * @since 0.16
     */
    void handleNotificationAcknowledged(PushNotificationResponse<?> response, long durationNanos);

    /**
     * Indicates that the client has successfully created a new connection to the APNs server in its internal
     * connection pool.
     *
     * @since 0.11
     */
    void handleConnectionAdded();

    /**
     * Indicates that the client has removed a previously-added connection from its internal connection pool.
     *
     * @since 0.11
     */
    void handleConnectionRemoved();

    /**
     * Indicates that an attempt to create a new connection to the APNs server failed.
     *
     * @since 0.11
     */
    void handleConnectionCreationFailed();
}
