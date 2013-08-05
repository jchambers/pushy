package com.relayrides.pushy;


public interface FailedDeliveryListener<T extends ApnsPushNotification> {
	void handleFailedDelivery(T notification, Throwable cause);
}
