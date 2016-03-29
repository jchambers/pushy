/* Copyright (c) 2013-2016 RelayRides
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
 * THE SOFTWARE. */

package com.relayrides.pushy.apns;

/**
 * <p>A metrics listener receives events from an {@link ApnsClient} that can be used to measure the performance and
 * behavior of the client. Although the information sent to a metrics listener is generally available by other means,
 * it is provided to listeners in a simplified form for ease of consumption and aggregation.</p>
 *
 * <p>The information provided to metrics listeners is intended only to measure the performance and behavior of an
 * {@code ApnsClient}; metrics listeners should never be used to drive business logic.</p>
 *
 * @see com.relayrides.pushy.apns.ApnsClient#setMetricsListener(ApnsClientMetricsListener)
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.6
 */
public interface ApnsClientMetricsListener {

    /**
     * Indicates that an attempt to send a push notification failed before the notification was processed by the APNs
     * server. Write failures may be the first event in a sequence for a given notification ID (indicating that the
     * notification was never written to the wire), but may also occur after a notification was sent if the connection
     * closed before the notification was acknowledged by the serer.
     *
     * @param apnsClient the client that sent the notification
     * @param notificationId an opaque identifier for the push notification that can be used to correlate this event
     * with other events related to the same notification
     *
     * @since 0.6
     */
    void handleWriteFailure(ApnsClient<? extends ApnsPushNotification> apnsClient, long notificationId);

    /**
     * Indicates that a notification was sent to the APNs server.
     *
     * @param apnsClient the client that sent the notification
     * @param notificationId an opaque identifier for the push notification that can be used to correlate this event
     * with other events related to the same notification
     *
     * @since 0.6
     */
    void handleNotificationSent(ApnsClient<? extends ApnsPushNotification> apnsClient, long notificationId);

    /**
     * Indicates that a notification that was previously sent to an APNs server was accepted by the server.
     *
     * @param apnsClient the client that sent the notification
     * @param notificationId an opaque identifier for the push notification that can be used to correlate this event
     * with other events related to the same notification
     *
     * @since 0.6
     */
    void handleNotificationAccepted(ApnsClient<? extends ApnsPushNotification> apnsClient, long notificationId);

    /**
     * Indicates that a notification that was previously sent to an APNs server was rejected by the server.
     *
     * @param apnsClient the client that sent the notification
     * @param notificationId an opaque identifier for the push notification that can be used to correlate this event
     * with other events related to the same notification
     *
     * @since 0.6
     */
    void handleNotificationRejected(ApnsClient<? extends ApnsPushNotification> apnsClient, long notificationId);

    /**
     * Indicates that the client has started an attempt to connect to an APNs server. This event will always be followed
     * by either a "connection attempt succeeded" or a "connection attempt failed" event; no other "connection attempt
     * started" events will be fired until the current attempt has resolved.
     *
     * @param apnsClient the client that started the connection attempt
     *
     * @since 0.6
     */
    void handleConnectionAttemptStarted(ApnsClient<? extends ApnsPushNotification> apnsClient);

    /**
     * Indicates that a previously-started connection attempt completed successfully.
     *
     * @param apnsClient the client that started the connection attempt
     *
     * @since 0.6
     */
    void handleConnectionAttemptSucceeded(ApnsClient<? extends ApnsPushNotification> apnsClient);

    /**
     * Indicates that a previously-started connection attempt failed.
     *
     * @param apnsClient the client that started the connection attempt
     *
     * @since 0.6
     */
    void handleConnectionAttemptFailed(ApnsClient<? extends ApnsPushNotification> apnsClient);
}
