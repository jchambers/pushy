package com.relayrides.pushy;

import java.util.Date;

public interface ApnsPushNotification {
	/**
	 * Returns the token of the device to which this push notification is to be sent.
	 * 
	 * @return an array of bytes containing the token of the device to which this notification is to be sent
	 * 
	 * @see <a href="https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ApplePushService.html#//apple_ref/doc/uid/TP40008194-CH100-SW9">
	 * Apple Push Notification Service</a>
	 */
	byte[] getToken();
	
	/**
	 * Returns the JSON-encoded payload of this push notification.
	 * 
	 * @return the JSON-encoded payload of this push notification
	 * 
	 * @see <a href="https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ApplePushService.html#//apple_ref/doc/uid/TP40008194-CH100-SW9">
	 * Apple Push Notification Service</a>
	 */
	String getPayload();
	
	/**
	 * Returns the time at which Apple's push notification service should stop trying to deliver this push notification
	 * to its destination. If null, the push notification service will not attempt to store the notification at all.
	 * 
	 * @return the time at which this notification can be discarded
	 * 
	 * @see <a href="https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/CommunicatingWIthAPS.html#//apple_ref/doc/uid/TP40008194-CH101-SW1">
	 * Provider Communication with Apple Push Notification Service</a>
	 */
	Date getDeliveryInvalidationTime();
}
