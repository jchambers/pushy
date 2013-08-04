package com.relayrides.pushy.apns;

public class ApnsException extends Exception {
	private static final long serialVersionUID = 1L;
	
	private final int notificationId;
	private final ApnsErrorCode errorCode;
	
	public ApnsException(final int notificationId, final ApnsErrorCode errorCode) {
		this.notificationId = notificationId;
		this.errorCode = errorCode;
	}
	
	public int getNotificationId() {
		return this.notificationId;
	}
	
	public ApnsErrorCode getErrorCode() {
		return this.errorCode;
	}
}
