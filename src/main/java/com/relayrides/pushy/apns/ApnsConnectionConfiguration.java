package com.relayrides.pushy.apns;

public abstract class ApnsConnectionConfiguration {

	private int connectTimeout = 10;
	private int sslHandshakeTimeout = 10;

	public ApnsConnectionConfiguration() {}

	public ApnsConnectionConfiguration(final ApnsConnectionConfiguration configuration) {
		this.connectTimeout = configuration.connectTimeout;
		this.sslHandshakeTimeout = configuration.sslHandshakeTimeout;
	}

	/**
	 * Returns the connect timeout for this configuration.
	 *
	 * @return the connect timeout in seconds
	 */
	public int getConnectTimeout() {
		return this.connectTimeout;
	}

	/**
	 * Sets the connect timeout, in seconds, for connections created with this configuration. By default, the connect
	 * timeout is ten seconds.
	 *
	 * @param connectTimeout the connect timeout in seconds; if zero, connection attempts will never time out
	 */
	public void setConnectTimeout(final int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	/**
	 * Returns the SSL handshake timeout for this configuration.
	 *
	 * @return the SSL handshake timeout in seconds
	 */
	public int getSslHandshakeTimeout() {
		return this.sslHandshakeTimeout;
	}

	/**
	 * Sets the SSL handshake timeout, in seconds, for connections created with this configuration. By default, the SSL
	 * handshake timeout is ten seconds.
	 *
	 * @param sslHandshakeTimeout the SSL handshake timeout in seconds
	 */
	public void setSslHandshakeTimeout(final int sslHandshakeTimeout) {
		this.sslHandshakeTimeout = sslHandshakeTimeout;
	}
}
