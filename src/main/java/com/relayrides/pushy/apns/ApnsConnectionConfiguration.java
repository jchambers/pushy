package com.relayrides.pushy.apns;

public class ApnsConnectionConfiguration {

	private int sentNotificationBufferCapacity = ApnsConnection.DEFAULT_SENT_NOTIFICATION_BUFFER_CAPACITY;

	public ApnsConnectionConfiguration() {}

	public ApnsConnectionConfiguration(final ApnsConnectionConfiguration configuration) {
		this.sentNotificationBufferCapacity = configuration.sentNotificationBufferCapacity;
	}

	public int getSentNotificationBufferCapacity() {
		return sentNotificationBufferCapacity;
	}

	public void setSentNotificationBufferCapacity(
			int sentNotificationBufferCapacity) {
		this.sentNotificationBufferCapacity = sentNotificationBufferCapacity;
	}
}
