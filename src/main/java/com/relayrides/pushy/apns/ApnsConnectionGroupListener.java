/* Copyright (c) 2015 RelayRides
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
 * An {@code ApnsConnectionGroupListener} receives lifecycle events from {@link ApnsConnection} instances in an
 * {@link ApnsConnectionGroup}. Handler methods are called from IO threads in the connection's event loop, and as such
 * handler method implementations <em>must not</em> perform blocking operations. Blocking operations should be
 * dispatched in separate threads.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public interface ApnsConnectionGroupListener<T extends ApnsPushNotification> {
	/**
	 * Indicates that the given connection group attempted to open a connection to an APNs gateway, but failed.
	 *
	 * @param group the connection group that failed to connect to an APNs gateway
	 * @param cause the cause of the failure
	 */
	void handleConnectionFailure(ApnsConnectionGroup<T> group, Throwable cause);

	/**
	 * Indicates that a connection in the given connection group failed to send a push notification to an APNs gateway.
	 * This indicates a local failure; notifications passed to this method were never transmitted to the APNs gateway,
	 * and failures of this kind generally represent temporary I/O problems (rather than permanent rejection by the
	 * gateway), and it is generally safe to try to send the failed notifications again later.
	 *
	 * @param group the connection group that attempted to deliver the notification
	 * @param notification the notification that could not be written
	 * @param cause the cause of the write failure
	 */
	void handleWriteFailure(ApnsConnectionGroup<T> group, T notification, Throwable cause);

	/**
	 * Indicates that a notification sent via the given connection group was definitively rejected by the APNs gateway.
	 * When an APNs gateway rejects a notification, the rejection should be considered a permanent failure and the
	 * notification should not be sent again.
	 *
	 * @param group the connection group that sent the notification that was rejected
	 * @param rejectedNotification the notification that was rejected
	 * @param reason the reason for the rejection
	 *
	 * @see ApnsConnectionGroupListener#handleUnprocessedNotifications(ApnsConnectionGroup, Collection)
	 */
	void handleRejectedNotification(ApnsConnectionGroup<T> group, T rejectedNotification, RejectedNotificationReason reason);

	/**
	 * Indicates that notifications that had previously been sent to an APNs gateway by the given connection group were
	 * not processed by the gateway and should be sent again later. This generally happens after a notification has been
	 * rejected by the gateway, but may also happen when a connection is closed gracefully by Pushy or closed remotely
	 * by the APNs gateway without a rejected notification.
	 *
	 * @param group the connection group that sent the notifications that were not processed
	 * @param unprocessedNotifications the notifications known to have not been processed by the APNs gateway
	 */
	void handleUnprocessedNotifications(ApnsConnectionGroup<T> group, Collection<T> unprocessedNotifications);
}
