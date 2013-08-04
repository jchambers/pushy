package com.relayrides.pushy;


public class SendableApnsPushNotification<T extends ApnsPushNotification> {
	private final T pushNotification;
	private final int notificationId;
	
	public SendableApnsPushNotification(final T pushNotification, final int notificationId) {
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
