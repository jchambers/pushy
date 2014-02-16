package com.relayrides.pushy.apns;

import javax.net.ssl.SSLHandshakeException;

/**
 * <p>Listens for failed attempts to connect to an APNs gateway. Generally, a push manager will continue to try to
 * connect until it is shut down (under the assumption that failures are temporary).</p>
 * 
 * <p>Some causes of failure are more permanent; generally, an {@link SSLHandshakeException} indicates a problem with
 * SSL credentials that is unlikely to be resolved by retrying the connection, and applications using Pushy are
 * encouraged to register a listener that shuts down a push manager in the event of a handshake exception.</p>
 * 
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public interface FailedConnectionListener<T extends ApnsPushNotification> {

	/**
	 * Handles a failed attempt to connect to the APNs gateway.
	 * 
	 * @param pushManager the push manager that failed to open a connection
	 * @param cause the cause for the connection failure
	 */
	void handleFailedConnection(PushManager<? extends T> pushManager, Throwable cause);
}
