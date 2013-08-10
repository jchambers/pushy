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
	 * Returns the time at which Apple's push notification service should stop trying to deliver this push notification
	 * to its destination. If @{code null}, the push notification service will not attempt to store the notification at
	 * all. Note that APNs will only store one notification per device token for redelivery at a time.
	 * 
	 * @return the time at which this notification can be discarded
	 * 
	 * @see <a href="https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/CommunicatingWIthAPS.html#//apple_ref/doc/uid/TP40008194-CH101-SW1">
	 * Provider Communication with Apple Push Notification Service</a>
	 */
	Date getDeliveryInvalidationTime();
}
