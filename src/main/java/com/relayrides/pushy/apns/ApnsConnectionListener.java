package com.relayrides.pushy.apns;

import java.util.Collection;

public interface ApnsConnectionListener<T extends ApnsPushNotification> {

	void handleConnectionSuccess(ApnsConnection<T> connection);
	void handleConnectionFailure(ApnsConnection<T> connection, Throwable cause);
	void handleConnectionClosure(ApnsConnection<T> connection);
	void handleWriteFailure(ApnsConnection<T> connection, T notification, Throwable cause);
	void handleRejectedNotification(ApnsConnection<T> connection, T rejectedNotification, RejectedNotificationReason reason, Collection<T> unprocessedNotifications);
}
