package com.relayrides.pushy.apns;

import com.relayrides.pushy.PushNotification;

public class SendableApnsPushNotification {
	private final PushNotification pushNotification;
	private final int notificationId;
	
	public SendableApnsPushNotification(final PushNotification pushNotification, final int notificationId) {
		this.pushNotification = pushNotification;
		this.notificationId = notificationId;
	}
	
	public PushNotification getPushNotification() {
		return this.pushNotification;
	}
	
	public int getNotificationId() {
		return this.notificationId;
	}
}
