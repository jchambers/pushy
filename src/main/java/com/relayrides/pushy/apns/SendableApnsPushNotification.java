package com.relayrides.pushy.apns;

import com.relayrides.pushy.ApnsPushNotification;

public class SendableApnsPushNotification {
	private final ApnsPushNotification pushNotification;
	private final int notificationId;
	
	public SendableApnsPushNotification(final ApnsPushNotification pushNotification, final int notificationId) {
		this.pushNotification = pushNotification;
		this.notificationId = notificationId;
	}
	
	public ApnsPushNotification getPushNotification() {
		return this.pushNotification;
	}
	
	public int getNotificationId() {
		return this.notificationId;
	}
}
