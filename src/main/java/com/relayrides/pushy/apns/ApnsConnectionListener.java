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
 * An {@code ApnsConnectionListener} receives lifecycle events from {@link ApnsConnection} instances. Handler methods
 * are called from IO threads in the connection's event loop, and as such handler method implementations <em>must
 * not</em> perform blocking operations. Blocking operations should be dispatched in separate threads.
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
	 * Indicates that the given connection has changed its writability state. Attempts to write to an unwritable
	 * connection are guaranteed to fail and should be avoided. Successful connections begin in a writable state.
	 *
	 * @param connection the connection whose writability has changed
	 * @param writable {@code true} if the connection has become writable or {@code false} if it has become unwritable
	 */
	void handleConnectionWritabilityChange(ApnsConnection<T> connection, boolean writable);

	/**
	 * Indicates that the given connection has disconnected from the previously-connected APNs gateway and can no
	 * longer send push notifications. This may happen either when the connection is closed locally or when the APNs
	 * gateway closes the connection remotely. This method will only be called if the connection had previously
	 * succeeded and completed a TLS handshake.
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
	 * Indicates that a notification sent via the given connection was definitively rejected by the APNs gateway. When
	 * an APNs gateway rejects a notification, the rejection should be considered a permanent failure and the
	 * notification should not be sent again. The APNs gateway will close the connection after rejecting a notification,
	 * and all notifications sent after the rejected notification were not processed by the gateway and should be
	 * re-sent later.
	 *
	 * @param connection the connection that sent the notification that was rejected
	 * @param rejectedNotification the notification that was rejected
	 * @param reason the reason for the rejection
	 *
	 * @see ApnsConnectionListener#handleConnectionClosure(ApnsConnection)
	 * @see ApnsConnectionListener#handleUnprocessedNotifications(ApnsConnection, Collection)
	 */
	void handleRejectedNotification(ApnsConnection<T> connection, T rejectedNotification, RejectedNotificationReason reason);

	/**
	 * Indicates that notifications that had previously been sent to an APNs gateway by the given connection were not
	 * processed by the gateway and should be sent again later. This generally happens after a notification has been
	 * rejected by the gateway, but may also happen when a connection is closed gracefully by Pushy or closed remotely
	 * by the APNs gateway without a rejected notification.
	 *
	 * @param connection the connection that sent the notifications that were not processed
	 * @param unprocessedNotifications the notifications known to have not been processed by the APNs gateway
	 */
	void handleUnprocessedNotifications(ApnsConnection<T> connection, Collection<T> unprocessedNotifications);
}
