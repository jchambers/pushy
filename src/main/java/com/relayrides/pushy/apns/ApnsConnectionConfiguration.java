/* Copyright (c) 2014 RelayRides
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.relayrides.pushy.apns;

/**
 * A set of user-configurable options common to connections to the various kinds of servers in an APNs environment.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public abstract class ApnsConnectionConfiguration {

	private int connectTimeout = 10;
	private int sslHandshakeTimeout = 10;

	/**
	 * Creates a new connection configuration object with all options set to their default values.
	 */
	public ApnsConnectionConfiguration() {}

	/**
	 * Creates a new connection configuration object with all options set to the values in the given connection
	 * configuration object.
	 *
	 * @param configuration the configuration object to copy
	 */
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
	 * @param sslHandshakeTimeout the SSL handshake timeout in seconds; if zero, SSL handshake attempts will never time
	 * out
	 */
	public void setSslHandshakeTimeout(final int sslHandshakeTimeout) {
		this.sslHandshakeTimeout = sslHandshakeTimeout;
	}
}
