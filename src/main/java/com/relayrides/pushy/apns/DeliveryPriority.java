package com.relayrides.pushy.apns;

/**
 * An enumeration of delivery priorities for APNs push notifications. Note that this priority affects when the
 * notification may be delivered to the receiving device by the APNs gateway and does <em>not</em> effect when the
 * notification will be sent to the gateway itself.
 * 
 * @see <a href="https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/CommunicatingWIthAPS.html#//apple_ref/doc/uid/TP40008194-CH101-SW4">
 * Local and Push Notification Programming Guide, Provider Communication with Apple Push Notification Service, The
 * Binary Interface and Notification Format</a>
 * 
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public enum DeliveryPriority {

	/**
	 * <p>According to Apple's documentation:</p>
	 * 
	 * <blockquote>
	 * 	<p>The push message is sent immediately.</p>
	 * 	<p>The push notification must trigger an alert, sound, or badge on the device. It is an error to use this
	 * 	priority for a push that contains only the {@code content-available} key.</p>
	 * </blockquote>
	 */
	IMMEDIATE((byte)10),

	/**
	 * <p>According to Apple's documentation:</p>
	 * 
	 * <blockquote><p>The push message is sent at a time that conserves power on the device receiving
	 * it.</p></blockquote>
	 */
	CONSERVE_POWER((byte)5);

	private final byte code;

	private DeliveryPriority(final byte code) {
		this.code = code;
	}

	protected byte getCode() {
		return this.code;
	}

	protected static DeliveryPriority getFromCode(final byte code) {
		for (final DeliveryPriority priority : DeliveryPriority.values()) {
			if (priority.getCode() == code) {
				return priority;
			}
		}

		throw new IllegalArgumentException(String.format("No delivery priority found with code %d", code));
	}
}
