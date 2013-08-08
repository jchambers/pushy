package com.relayrides.pushy.apns;

/**
 * <p>Represents a push notification wrapped with transmission-related metadata ready to be sent to an APNs server.
 * Sendable push notifications include a sequence number that can be used to identify notifications rejected by the
 * APNs server.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 * @param <T>
 */
public class SendableApnsPushNotification<T extends ApnsPushNotification> {
	private final T pushNotification;
	private final int sequenceNumber;
	
	/**
	 * Constructs a sendable push notification with the given base notification and sequence number.
	 * 
	 * @param pushNotification the underlying push notification
	 * @param sequenceNumber the channel-specific sequence number with which to send this notification
	 */
	public SendableApnsPushNotification(final T pushNotification, final int sequenceNumber) {
		this.pushNotification = pushNotification;
		this.sequenceNumber = sequenceNumber;
	}
	
	/**
	 * Returns the push notification to be sent.
	 * 
	 * @return the push notification to be sent
	 */
	public T getPushNotification() {
		return this.pushNotification;
	}
	
	/**
	 * Returns the channel-specific sequence number for this push notification.
	 * 
	 * @return the channel-specific sequence number for this push notification
	 */
	public int getSequenceNumber() {
		return this.sequenceNumber;
	}
}
