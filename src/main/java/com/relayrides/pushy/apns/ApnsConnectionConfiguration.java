package com.relayrides.pushy.apns;

/**
 * A set of user-configurable options that affect the behavior of an {@link ApnsConnection}.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class ApnsConnectionConfiguration {

	private int sentNotificationBufferCapacity = ApnsConnection.DEFAULT_SENT_NOTIFICATION_BUFFER_CAPACITY;

	/**
	 * Creates a new connection configuration object with all options set to their default values.
	 */
	public ApnsConnectionConfiguration() {}

	/**
	 * Creates a new connection configuration object with all options set to the values in the given connection
	 * configuration object.
	 *
	 * @param configuration the configuration object to copy
	 */
	public ApnsConnectionConfiguration(final ApnsConnectionConfiguration configuration) {
		this.sentNotificationBufferCapacity = configuration.sentNotificationBufferCapacity;
	}

	/**
	 * Returns the sent notification buffer capacity for connections created with this configuration.
	 *
	 * @return the sent notification buffer capacity for connections created with this configuration
	 */
	public int getSentNotificationBufferCapacity() {
		return sentNotificationBufferCapacity;
	}

	/**
	 * Sets the sent notification buffer capacity for connections created with this configuration. The default capacity
	 * is {@value ApnsConnection#DEFAULT_SENT_NOTIFICATION_BUFFER_CAPACITY} notifications.
	 *
	 * @param sentNotificationBufferCapacity the sent notification buffer capacity for connections created with this
	 * configuration
	 */
	public void setSentNotificationBufferCapacity(int sentNotificationBufferCapacity) {
		this.sentNotificationBufferCapacity = sentNotificationBufferCapacity;
	}
}
