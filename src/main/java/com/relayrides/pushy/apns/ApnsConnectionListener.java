/* Copyright (c) 2013 RelayRides
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

package com.relayrides.pushy.apns;

import java.util.Collection;

/**
 * An {@code ApnsConnectionListener} receives lifecycle events from {@link ApnsConnection} instances.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public interface ApnsConnectionListener<T extends ApnsPushNotification> {

	/**
	 * Indicates that the given connection successfully connected to an APNs gateway and is ready to send push
	 * notifications.
	 *
	 * @param connection the connection that completed its connection attempt
	 */
	void handleConnectionSuccess(ApnsConnection<T> connection);

	/**
	 * Indicates that the given connection attempted to connect to an APNs gateway, but failed.
	 *
	 * @param connection the connection that failed to connect to an APNs gateway
	 * @param cause the cause of the failure
	 */
	void handleConnectionFailure(ApnsConnection<T> connection, Throwable cause);

	/**
	 * Indicates that the given connection has disconnected from the previously-connected APNs gateway and can no
	 * longer send push notifications. This may happen either when the connection is closed locally or when the APNs
	 * gateway closes the connection remotely.
	 *
	 * @param connection the connection that has been disconnected and is no longer active
	 */
	void handleConnectionClosure(ApnsConnection<T> connection);

	/**
	 * Indicates that the given connection failed to send a push notification to an APNs gateway. This indicates a
	 * local failure; notifications passed to this method were never transmitted to the APNs gateway, and failures of
	 * this kind generally represent temporary I/O problems (rather than permanent rejection by the gateway), and it
	 * is generally safe to try to send the failed notifications again later.
	 *
	 * @param connection the connection that attempted to deliver the notification
	 * @param notification the notification that could not be written
	 * @param cause the cause of the write failure
	 */
	void handleWriteFailure(ApnsConnection<T> connection, T notification, Throwable cause);

	/**
	 * <p>Indicates that a notification sent via the given connection was definitively rejected by the APNs gateway.
	 * When an APNs gateway rejects a notification, the rejection should be considered a permanent failure and the
	 * notification should not be sent again (exception: some notifications may be rejected with the
	 * {@link RejectedNotificationReason#SHUTDOWN} status code, which indicates that the notification was processed
	 * successfully, but the connection was closed regardless).</p>
	 *
	 * <p>The APNs gateway will close the connection after rejecting a notification, and all notifications sent after
	 * the rejected notification were not processed by the gateway and should be re-sent at a later time. The collection
	 * of unprocessed notifications is passed as an argument to this method, and callers generally do not need to
	 * maintain a buffer of sent notifications on their own.</p>
	 *
	 * <p>When shutting down a connection gracefully, an {@link ApnsConnection} will send a known-bad notification to
	 * the gateway, and this method will be called with a {@code null} rejected notification and rejection reason. The
	 * rejected notification may also be {@code null} if the connection was unable to retrieve the rejected notification
	 * from its sent notification buffer, but this should be considered an error case and reported as a bug. In this
	 * case, the rejection reason will be non-null.</p>
	 *
	 * @param connection the connection that sent the notification that was rejected
	 * @param rejectedNotification the notification that was rejected (may be {@code null})
	 * @param reason the reason for the rejection; this will be {@code null} if the rejected notification was a known-bad
	 * shutdown notification
	 * @param unprocessedNotifications a collection of all notifications sent after the rejected notification; these
	 * notifications were not processed by the APNs gateway and may be re-sent later
	 *
	 * @see ApnsConnection#shutdownGracefully()
	 * @see RejectedNotificationReason#SHUTDOWN
	 */
	void handleRejectedNotification(ApnsConnection<T> connection, T rejectedNotification, RejectedNotificationReason reason, Collection<T> unprocessedNotifications);
}
