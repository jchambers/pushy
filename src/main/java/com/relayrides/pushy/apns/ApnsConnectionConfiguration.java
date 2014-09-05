package com.relayrides.pushy.apns;

/**
 * A set of user-configurable options that affect the behavior of an {@link ApnsConnection}.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class ApnsConnectionConfiguration {

	private int sentNotificationBufferCapacity = ApnsConnection.DEFAULT_SENT_NOTIFICATION_BUFFER_CAPACITY;
	private Integer closeAfterInactivityTime = null;
	private Integer gracefulShutdownTimeout = null;

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
	 * is {@value ApnsConnection#DEFAULT_SENT_NOTIFICATION_BUFFER_CAPACITY} notifications. While sent notification
	 * buffers may have any positive capacity, it is not recommended that they be given a capacity less than the
	 * default.
	 *
	 * @param sentNotificationBufferCapacity the sent notification buffer capacity for connections created with this
	 * configuration
	 */
	public void setSentNotificationBufferCapacity(final int sentNotificationBufferCapacity) {
		this.sentNotificationBufferCapacity = sentNotificationBufferCapacity;
	}

	/**
	 * Returns the time, in seconds, since the last push notification was sent after which connections created with this
	 * configuration will be closed.
	 *
	 * @return the time, in seconds, since the last push notification was sent after which connections created with this
	 * configuration will be closed
	 */
	public Integer getCloseAfterInactivityTime() {
		return this.closeAfterInactivityTime;
	}

	/**
	 * Sets the time, in seconds, since the last push notification was sent after which connections created with this
	 * configuration will be closed. If {@code null} (the default), connections will never be closed due to inactivity.
	 *
	 * @param closeAfterInactivityTime the time, in seconds since the last push notification was sent, after which
	 * connections will be closed
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
		if (sentNotificationBufferCapacity != other.sentNotificationBufferCapacity)
			return false;
		return true;
	}

}
