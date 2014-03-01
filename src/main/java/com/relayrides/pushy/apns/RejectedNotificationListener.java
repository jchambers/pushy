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

/**
 * <p>Listens for permanent push notification rejections. Listeners will be informed when the APNs server permanently
 * rejects a notification for a specific reason. Listeners are <em>not</em> notified of temporary delivery issues.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 * 
 * @see <a href="http://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/CommunicatingWIthAPS.html#//apple_ref/doc/uid/TP40008194-CH101-SW4">
 * Local and Push Notification Programming Guide - Provider Communication with Apple Push Notification Service - The
 * Binary Interface and Notification Formats</a>
 * 
 * @see com.relayrides.pushy.apns.PushManager#registerRejectedNotificationListener(RejectedNotificationListener)
 * @see com.relayrides.pushy.apns.PushManager#unregisterRejectedNotificationListener(RejectedNotificationListener)
 */
public interface RejectedNotificationListener<T extends ApnsPushNotification> {

	/**
	 * Handles a permanent push notification rejection.
	 * 
	 * @param pushManager the push manager that sent the rejected notification
	 * @param notification the notification rejected by the APNs server
	 * @param rejectionReason the reason reported by APNs for the rejection
	 */
	void handleRejectedNotification(PushManager<? extends T> pushManager, T notification, RejectedNotificationReason rejectionReason);
}
