package com.relayrides.pushy.apns;

public class MemorySentNotificationBufferProvider<E extends ApnsPushNotification>
		implements SentNotificationBufferProvider<E> {

	private final int capacity;

	public MemorySentNotificationBufferProvider(int capacity) {
		this.capacity = capacity;
	}

	@Override
	public SentNotificationBuffer<E> get() {
		return new MemorySentNotificationBuffer<E>(capacity);
	}

}
