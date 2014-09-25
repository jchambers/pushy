package com.relayrides.pushy.apns;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

class TestConnectionListener implements FeedbackConnectionListener, PushNotificationConnectionListener<SimpleApnsPushNotification> {

	private final Object mutex;

	private boolean connectionSucceeded = false;
	private boolean connectionFailed = false;
	private boolean connectionClosed = false;

	private Throwable connectionFailureCause;

	private final ArrayList<SimpleApnsPushNotification> writeFailures = new ArrayList<SimpleApnsPushNotification>();

	private SimpleApnsPushNotification rejectedNotification;
	private RejectedNotificationReason rejectionReason;

	private final ArrayList<SimpleApnsPushNotification> unprocessedNotifications = new ArrayList<SimpleApnsPushNotification>();

	private final ArrayList<ExpiredToken> expiredTokens = new ArrayList<ExpiredToken>();

	public TestConnectionListener(final Object mutex) {
		this.mutex = mutex;
	}

	@Override
	public void handleConnectionSuccess(final ApnsConnection connection) {
		synchronized (this.mutex) {
			this.connectionSucceeded = true;
			this.mutex.notifyAll();
		}
	}

	@Override
	public void handleConnectionFailure(final ApnsConnection connection, final Throwable cause) {
		synchronized (this.mutex) {
			this.connectionFailed = true;
			this.connectionFailureCause = cause;

			this.mutex.notifyAll();
		}
	}

	@Override
	public void handleConnectionClosure(final ApnsConnection connection) {
		synchronized (this.mutex) {
			this.connectionClosed = true;

			this.mutex.notifyAll();
		}
	}

	@Override
	public void handleConnectionWritabilityChange(final PushNotificationConnection<SimpleApnsPushNotification> connection,
			final boolean writable) {
		// For now, we're just ignoring this
	}

	@Override
	public void handleWriteFailure(final PushNotificationConnection<SimpleApnsPushNotification> connection,
			final SimpleApnsPushNotification notification, final Throwable cause) {

		this.writeFailures.add(notification);
	}

	@Override
	public void handleRejectedNotification(final PushNotificationConnection<SimpleApnsPushNotification> connection,
			final SimpleApnsPushNotification rejectedNotification, final RejectedNotificationReason reason) {

		this.rejectedNotification = rejectedNotification;
		this.rejectionReason = reason;
	}

	@Override
	public void handleUnprocessedNotifications(final PushNotificationConnection<SimpleApnsPushNotification> connection,
			final Collection<SimpleApnsPushNotification> unprocessedNotifications) {

		this.unprocessedNotifications.addAll(unprocessedNotifications);
	}

	@Override
	public void handleExpiredToken(final FeedbackConnection connection, final ExpiredToken token) {
		this.expiredTokens.add(token);
	}

	public Object getMutex() {
		return this.mutex;
	}

	public boolean hasConnectionSucceeded() {
		return this.connectionSucceeded;
	}

	public boolean hasConnectionFailed() {
		return this.connectionFailed;
	}

	public Throwable getConnectionFailureCause() {
		return this.connectionFailureCause;
	}

	public boolean hasConnectionClosed() {
		return this.connectionClosed;
	}

	public SimpleApnsPushNotification getRejectedNotification() {
		return this.rejectedNotification;
	}

	public RejectedNotificationReason getRejectionReason() {
		return this.rejectionReason;
	}

	public List<SimpleApnsPushNotification> getUnprocessedNotifications() {
		return new ArrayList<SimpleApnsPushNotification>(this.unprocessedNotifications);
	}

	public List<ExpiredToken> getExpiredTokens() {
		return new ArrayList<ExpiredToken>(this.expiredTokens);
	}
}
