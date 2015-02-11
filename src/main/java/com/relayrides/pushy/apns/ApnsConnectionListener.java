package com.relayrides.pushy.apns;

public interface ApnsConnectionListener {
	/**
	 * Indicates that the given connection successfully connected to the APNs feedback service and will receive expired
	 * when they are sent by the feedback service.
	 *
	 * @param connection the connection that completed its connection attempt
	 */
	void handleConnectionSuccess(ApnsConnection connection);

	/**
	 * Indicates that the given connection attempted to connect to the APNs feedback service, but failed.
	 *
	 * @param connection the connection that failed to connect to the APNs feedback service
	 * @param cause the cause of the failure
	 */
	void handleConnectionFailure(ApnsConnection connection, Throwable cause);

	/**
	 * Indicates that the given connection has disconnected from the APNs feedback service and will no longer receive
	 * expired tokens. This method will only be called if the connection had previously succeeded and completed a TLS
	 * handshake.
	 *
	 * @param connection the connection that has been disconnected and is no longer active
	 */
	void handleConnectionClosure(ApnsConnection connection);
}
