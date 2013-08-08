package com.relayrides.pushy.apns;


public interface FailedDeliveryListener<T extends ApnsPushNotification> {
	void handleFailedDelivery(T notification, ApnsException cause);
}
