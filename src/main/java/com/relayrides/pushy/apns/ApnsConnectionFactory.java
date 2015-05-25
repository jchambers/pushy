package com.relayrides.pushy.apns;

public interface ApnsConnectionFactory<T extends ApnsPushNotification> {
	ApnsConnection<T> createApnsConnection();
}
