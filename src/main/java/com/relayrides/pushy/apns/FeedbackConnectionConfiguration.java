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
 * A set of user-configurable options that affect the behavior of a {@link FeedbackServiceConnection}.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class FeedbackConnectionConfiguration {
	private int readTimeout = 1;

	/**
	 * Creates a new connection configuration object with all options set to their default values.
	 */
	public FeedbackConnectionConfiguration() {}

	/**
	 * Creates a new connection configuration object with all options set to the values in the given connection
	 * configuration object.
	 *
	 * @param configuration the configuration object to copy
	 */
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
			throw new IllegalArgumentException("Read timeout must be greater than zero.");
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
