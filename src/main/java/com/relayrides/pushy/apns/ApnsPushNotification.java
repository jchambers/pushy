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

import java.util.Date;

import com.relayrides.pushy.apns.util.ApnsPayloadBuilder;
import com.relayrides.pushy.apns.util.TokenUtil;

/**
 * <p>A push notification that can be sent through the Apple Push Notification service (APNs). Push notifications have
 * a token that identifies the device to which it should be sent, a JSON payload, and (optionally) a time at which the
 * notification is invalid and should no longer be delivered.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 * @see <a href="https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ApplePushService.html#//apple_ref/doc/uid/TP40008194-CH100-SW9">
 * Apple Push Notification Service</a>
 * 
 * @see TokenUtil
 * @see ApnsPayloadBuilder
 */
public interface ApnsPushNotification {
	/**
	 * Returns the token of the device to which this push notification is to be sent.
	 * 
	 * @return an array of bytes containing the token of the device to which this notification is to be sent
	 */
	byte[] getToken();

	/**
	 * Returns the JSON-encoded payload of this push notification.
	 * 
	 * @return the JSON-encoded payload of this push notification
	 */
	String getPayload();

	/**
	 * Returns the time at which Apple's push notification service should stop trying to deliver this push notification.
	 * If {@code null}, the push notification service will not attempt to store the notification at all. Note that APNs
	 * will only store one notification per device token for redelivery at a time.
	 * 
	 * @return the time at which this notification can be discarded
	 * 
	 * @see <a href="https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/CommunicatingWIthAPS.html#//apple_ref/doc/uid/TP40008194-CH101-SW1">
	 * Provider Communication with Apple Push Notification Service</a>
	 */
	Date getDeliveryInvalidationTime();

	/**
	 * Returns the priority with which this push notification should be sent to the receiving device. If {@code null},
	 * an immediate delivery priority is assumed.
	 * 
	 * @return the priority with which this push notification should be sent to the receiving device
	 * 
	 * @see <a href="https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/CommunicatingWIthAPS.html#//apple_ref/doc/uid/TP40008194-CH101-SW1">
	 * Provider Communication with Apple Push Notification Service</a>
	 */
	DeliveryPriority getPriority();
}
