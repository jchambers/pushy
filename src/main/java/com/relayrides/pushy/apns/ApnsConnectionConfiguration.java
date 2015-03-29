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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;

/**
 * A set of user-configurable options that affect the behavior of an {@link ApnsConnection}.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class ApnsConnectionConfiguration {

	private ChannelHandler proxyHandler;
	private CustomBootstrapConfiguration customBootstrapConfiguration;
	private int sentNotificationBufferCapacity = ApnsConnection.DEFAULT_SENT_NOTIFICATION_BUFFER_CAPACITY;
	private Integer closeAfterInactivityTime = null;
	private Integer gracefulShutdownTimeout = null;
	private Integer sendAttemptLimit = null;

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
		this.sentNotificationBufferCapacity = configuration.sentNotificationBufferCapacity;
		this.closeAfterInactivityTime = configuration.closeAfterInactivityTime;
		this.gracefulShutdownTimeout = configuration.gracefulShutdownTimeout;
		this.sendAttemptLimit = configuration.sendAttemptLimit;
		this.proxyHandler = configuration.proxyHandler;
		this.customBootstrapConfiguration =configuration.customBootstrapConfiguration;
	}

	/**
	 * Returns the sent notification buffer capacity for connections created with this configuration.
	 *
	 * @return the sent notification buffer capacity for connections created with this configuration
	 */
	public int getSentNotificationBufferCapacity() {
		return sentNotificationBufferCapacity;
	}

	/**
	 * Sets the sent notification buffer capacity for connections created with this configuration. The default capacity
	 * is {@value com.relayrides.pushy.apns.ApnsConnection#DEFAULT_SENT_NOTIFICATION_BUFFER_CAPACITY} notifications.
	 * While sent notification buffers may have any positive capacity, it is not recommended that they be given a
	 * capacity less than the default.
	 *
	 * @param sentNotificationBufferCapacity the sent notification buffer capacity for connections created with this
	 * configuration
	 */
	public void setSentNotificationBufferCapacity(final int sentNotificationBufferCapacity) {
		this.sentNotificationBufferCapacity = sentNotificationBufferCapacity;
	}

	/**
	 * Returns the time, in seconds, between the sending of the last push notification and connection closure. If
	 * {@code null}, connections created with this configuration will never be closed due to inactivity.
	 *
	 * @return the time, in seconds, between the sending of the last push notification and connection closure
	 */
	public Integer getCloseAfterInactivityTime() {
		return this.closeAfterInactivityTime;
	}

	/**
	 * Sets the time, in seconds, between the sending of the last push notification and connection closure. If
	 * {@code null} (the default), connections will never be closed due to inactivity.
	 *
	 * @param closeAfterInactivityTime the time, in seconds, between the sending of the last push notification and
	 * connection closure
	 */
	public void setCloseAfterInactivityTime(final Integer closeAfterInactivityTime) {
		this.closeAfterInactivityTime = closeAfterInactivityTime;
	}

	/**
	 * Returns the time, in seconds, after which a graceful shutdown attempt should be abandoned and the connection
	 * should be closed immediately.
	 *
	 * @return the time, in seconds, after which a graceful shutdown attempt should be abandoned and the connection
	 * should be closed immediately
	 */
	public Integer getGracefulShutdownTimeout() {
		return this.gracefulShutdownTimeout;
	}

	/**
	 * Sets the time, in seconds, after which a graceful shutdown attempt should be abandoned and the connection should
	 * be closed immediately. If {@code null} (the default) graceful shutdown attempts will never time out. Note that,
	 * if a graceful shutdown attempt times out, no guarantees are made as to the state of notifications sent by the
	 * connection.
	 *
	 * @param gracefulShutdownTimeout the time, in seconds, after which a graceful shutdown attempt should be abandoned
	 */
	public void setGracefulShutdownTimeout(final Integer gracefulShutdownTimeout) {
		this.gracefulShutdownTimeout = gracefulShutdownTimeout;
	}

	/**
	 * Returns the number of notifications a connection may attempt to send before shutting down.
	 *
	 * @return the number of notifications a connection may attempt to send before shutting down, or {@code null} if no
	 * limit has been set
	 */
	public Integer getSendAttemptLimit() {
		return this.sendAttemptLimit;
	}

	/**
	 * <p>Sets the number of notifications a connection may attempt to send before shutting down. If not {@code null},
	 * connections will attempt to shut down gracefully after the given number of send attempts regardless of whether
	 * those attempts were actually successful. By default, no limit is set.</p>
	 *
	 * <p>If the a send attempt limit is set and is less than the sent notification buffer size, it is guaranteed that
	 * notifications will never be lost due to buffer overruns (though they may be lost by other means, such as non-
	 * graceful shutdowns).</p>
	 *
	 * @param sendAttemptLimit the number of notifications the connection may attempt to send before shutting down
	 * gracefully; if {@code null}, no limit is set
	 *
	 * @see ApnsConnectionConfiguration#setSentNotificationBufferCapacity(int)
	 */
	public void setSendAttemptLimit(final Integer sendAttemptLimit) {
		this.sendAttemptLimit = sendAttemptLimit;
	}

	/**
	 * <p>Set a channel handler that will be used as proxy handler. It will be added at first position
	 * in the channel pipeline.</p>
	 * <p>
	 *     Example:
	 * <pre>
	 *   config.setProxyHandler(new Socks5ProxyHandler(new InetSocketAddress(&lt;sock5.proxy.host&gt;, &lt;sock5.proxy.port&gt;)));
	 * </pre>
	 * </p>
	 * @param proxyHandler The proxy handler to add to the pipeline.
	 */
	public void setProxyHandler(ChannelHandler proxyHandler) {
		this.proxyHandler = proxyHandler;
	}

	/**
	 * Returns the channel handler that must be used to connect to a proxy.
	 * Returns {@code null} when no proxy are configured.
	 * @return Returns the channel handler that must be used to connect to a proxy.
	 */
	public ChannelHandler getProxyHandler() {
		return proxyHandler;
	}

	/**
	 * Returns the custom bootstrap configuration callback.
	 * @return Returns the custom bootstrap configuration callback.
	 */
	public CustomBootstrapConfiguration getCustomBootstrapConfiguration() {
		return customBootstrapConfiguration;
	}

	/**
	 * Set the custom bootstrap configuration callback.
	 * @param customBootstrapConfiguration The custom bootstrap configuration callback to set.
	 */
	public void setCustomBootstrapConfiguration(CustomBootstrapConfiguration customBootstrapConfiguration) {
		this.customBootstrapConfiguration = customBootstrapConfiguration;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime
				* result
				+ ((closeAfterInactivityTime == null) ? 0
						: closeAfterInactivityTime.hashCode());
		result = prime
				* result
				+ ((gracefulShutdownTimeout == null) ? 0
						: gracefulShutdownTimeout.hashCode());
		result = prime
				* result
				+ ((sendAttemptLimit == null) ? 0 : sendAttemptLimit.hashCode());
		result = prime
				* result
				+ ((customBootstrapConfiguration == null) ? 0 : customBootstrapConfiguration.hashCode());
		result = prime
				* result
				+ ((proxyHandler == null) ? 0 : proxyHandler.hashCode());
		result = prime * result + sentNotificationBufferCapacity;
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final ApnsConnectionConfiguration other = (ApnsConnectionConfiguration) obj;
		if (closeAfterInactivityTime == null) {
			if (other.closeAfterInactivityTime != null)
				return false;
		} else if (!closeAfterInactivityTime
				.equals(other.closeAfterInactivityTime))
            return false;
		if (gracefulShutdownTimeout == null) {
			if (other.gracefulShutdownTimeout != null)
				return false;
		} else if (!gracefulShutdownTimeout
				.equals(other.gracefulShutdownTimeout))
            return false;
		if (sendAttemptLimit == null) {
			if (other.sendAttemptLimit != null)
				return false;
		} else if (!sendAttemptLimit.equals(other.sendAttemptLimit))
			return false;
		if (proxyHandler == null) {
			if (other.proxyHandler != null)
				return false;
		} else if (!proxyHandler.equals(other.proxyHandler))
			return false;
		if (customBootstrapConfiguration == null) {
			if (other.customBootstrapConfiguration != null)
				return false;
		} else if (!customBootstrapConfiguration.equals(other.customBootstrapConfiguration))
			return false;
		if (sentNotificationBufferCapacity != other.sentNotificationBufferCapacity)
			return false;
		return true;
	}

}
