package com.relayrides.pushy.apns;

/**
 * A set of user-configurable options that affect the behavior of a {@link FeedbackServiceConnection}.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class FeedbackConnectionConfiguration {
	private int readTimeout = 1;

	public FeedbackConnectionConfiguration() {}

	public FeedbackConnectionConfiguration(final FeedbackConnectionConfiguration configuration) {
		this.readTimeout = configuration.readTimeout;
	}

	/**
	 * Returns the time, in seconds, after which this connection will be closed if no new expired tokens have been
	 * received.
	 *
	 * @return the time, in seconds, after which this connection will be closed if no new expired tokens have been
	 * received
	 */
	public int getReadTimeout() {
		return this.readTimeout;
	}

	/**
	 * Sets the time, in seconds, after which this connection will be closed if no new expired tokens have been
	 * received.
	 *
	 * @param readTimeout the time, in seconds, after which this connection will be closed if no new expired tokens
	 * have been received; must be positive
	 */
	public void setReadTimeout(final int readTimeout) {
		if (readTimeout < 1) {
			throw new IllegalArgumentException("Read timeout must be positive.");
		}

		this.readTimeout = readTimeout;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + readTimeout;
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
		final FeedbackConnectionConfiguration other = (FeedbackConnectionConfiguration) obj;
		if (readTimeout != other.readTimeout)
			return false;
		return true;
	}
}
