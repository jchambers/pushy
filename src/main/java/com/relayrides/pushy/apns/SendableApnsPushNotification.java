package com.relayrides.pushy.apns;

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
	
	protected boolean isSequentiallyBefore(final int sequenceNumber) {
		// This works around integer wrapping in the unlikely (but awesome) case that a connection sends more than
		// Integer.MAX_VALUE notifications in its lifetime). Granted, this falls apart if we've managed to send
		// (Integer.MAX_VALUE * 2) notifications in between the time we send the bad message and the time we hear about
		// the error, but that seems like an acceptable risk.
		return (this.notificationId - sequenceNumber) < 0;
	}
}
