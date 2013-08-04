package com.relayrides.pushy;

import com.relayrides.pushy.apns.ApnsErrorCode;

public interface FailedDeliveryListener<T extends ApnsPushNotification> {
	void handleFailedDelivery(T notification, ApnsErrorCode errorCode);
}
