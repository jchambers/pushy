package com.relayrides.pushy.apns;

import com.relayrides.pushy.apns.ApnsPushNotification;

public class ReceivedApnsPushNotification<T extends ApnsPushNotification> {
	
	private final T pushNotification;
	private final int notificationId;
	
	public ReceivedApnsPushNotification(final T pushNotification, final int notificationId) {
		this.pushNotification = pushNotification;
		this.notificationId = notificationId;
	}
	
	public T getPushNotification() {
		return this.pushNotification;
	}
	
	public int getNotificationId() {
		return this.notificationId;
	}
}
