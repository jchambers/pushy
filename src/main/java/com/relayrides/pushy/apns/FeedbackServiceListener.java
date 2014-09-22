package com.relayrides.pushy.apns;

interface FeedbackServiceListener {

	void handleConnectionSuccess(FeedbackServiceConnection connection);

	void handleConnectionFailure(FeedbackServiceConnection connection, Throwable cause);

	void handleExpiredToken(FeedbackServiceConnection connection, ExpiredToken token);

	void handleConnectionClosure(FeedbackServiceConnection connection);
}
