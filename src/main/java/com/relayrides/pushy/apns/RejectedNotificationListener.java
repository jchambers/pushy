package com.relayrides.pushy.apns;

/**
 * <p>Listens for permanent push notification rejections. Listeners will be informed when the APNs server rejects a
 * notification for a specific reason. Listeners are not notified in cases where the client will attempt to re-send the
 * notification.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 * 
 * @see <a href="http://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/CommunicatingWIthAPS.html#//apple_ref/doc/uid/TP40008194-CH101-SW4">
 * Local and Push Notification Programming Guide - Provider Communication with Apple Push Notification Service - The
 * Binary Interface and Notification Formats</a>
 */
public interface RejectedNotificationListener<T extends ApnsPushNotification> {
	
	/**
	 * Handles a permanent push notification rejection.
	 *  
	 * @param notification the notification rejected by the APNs server
	 * @param cause the cause reported by APNs for the rejection
	 */
	void handleRejectedNotification(T notification, ApnsException cause);
}
