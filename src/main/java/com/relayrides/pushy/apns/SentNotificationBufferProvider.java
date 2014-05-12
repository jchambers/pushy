package com.relayrides.pushy.apns;

/**
 * Provides with new instances of SentNotificationBuffer hidding the actual implementation.
 * 
 * @author <a href="mailto:flozano@gmail.com">Francisco A. Lozano</a>
 *
 * @param <E>
 */
public interface SentNotificationBufferProvider<E extends ApnsPushNotification> {
	/**
	 * Obtain a new SentNotificationBuffer.
	 * 
	 */
	SentNotificationBuffer<E> get();
}
