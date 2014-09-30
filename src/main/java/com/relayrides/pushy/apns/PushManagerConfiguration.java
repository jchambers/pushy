/* Copyright (c) 2013 RelayRides
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
 * A set of user-configurable options that affect the behavior of a {@link PushManager} and its associated
 * {@link ApnsConnection}s.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class PushManagerConfiguration {

	private int concurrentConnectionCount = 1;

	private ApnsConnectionConfiguration connectionConfiguration = new ApnsConnectionConfiguration();
	private FeedbackConnectionConfiguration feedbackConfiguration = new FeedbackConnectionConfiguration();

	/**
	 * Constructs a new push manager configuration object with all options set to their default values.
	 */
	public PushManagerConfiguration() {}

	/**
	 * Constructs a new push manager configuration object with all options set to the values in the given configuration
	 * object.
	 *
	 * @param configuration the configuration object to copy
	 */
	public PushManagerConfiguration(final PushManagerConfiguration configuration) {
		this.concurrentConnectionCount = configuration.getConcurrentConnectionCount();

		this.connectionConfiguration = new ApnsConnectionConfiguration(configuration.getConnectionConfiguration());
		this.feedbackConfiguration = new FeedbackConnectionConfiguration(configuration.getFeedbackConnectionConfiguration());
	}

	/**
	 * Returns the number of concurrent connections to be maintained by push managers created with this configuration.
	 *
	 * @return the number of concurrent connections to be maintained by push managers created with this configuration
	 */
	public int getConcurrentConnectionCount() {
		return concurrentConnectionCount;
	}

	/**
	 * Sets the number of concurrent connections to be maintained by push managers created with this configuration. By
	 * default, push managers will maintain a single connection to the APNs gateway.
	 *
	 * @param concurrentConnectionCount the number of concurrent connections to be maintained by push managers created
	 * with this configuration
	 */
	public void setConcurrentConnectionCount(int concurrentConnectionCount) {
		this.concurrentConnectionCount = concurrentConnectionCount;
	}

	/**
	 * Returns the configuration to be used for connections created by push managers created with this configuration. A
	 * set of default connection options is used if none is specified.
	 *
	 * @return the configuration to be used for connections created by push managers created with this configuration
	 */
	public ApnsConnectionConfiguration getConnectionConfiguration() {
		return connectionConfiguration;
	}

	/**
	 * Sets the configuration to be used for connections created by push managers created with this configuration.
	 *
	 * @param connectionConfiguration the configuration to be used for connections created by push managers created with
	 * this configuration; must not be {@code null}
	 *
	 * @throws NullPointerException if the given connection configuration is {@code null}
	 */
	public void setConnectionConfiguration(final ApnsConnectionConfiguration connectionConfiguration) {
		if (connectionConfiguration == null) {
			throw new NullPointerException("Connection configuration must not be null.");
		}

		this.connectionConfiguration = connectionConfiguration;
	}

	/**
	 * Returns the configuration to be used for connections to the APNs feedback service created by push managers with
	 * this configuration. A set of default options is used if none is specified.
	 *
	 * @return the configuration to be used for connections to the APNs feedback service created by push managers with
	 * this configuration
	 */
	public FeedbackConnectionConfiguration getFeedbackConnectionConfiguration() {
		return this.feedbackConfiguration;
	}

	/**
	 * Sets the configuration to be used for connections to the APNs feedback service created by push managers with
	 * this configuration.
	 *
	 * @param feedbackConnectionConfiguration the configuration to be used for connections to the APNs feedback service
	 * created by push managers with this configuration; must not be {@code null}
	 *
	 * @throws NullPointerException if the given feedback connection configuration is {@code null}
	 */
	public void setFeedbackConnectionConfiguration(final FeedbackConnectionConfiguration feedbackConnectionConfiguration) {
		if (feedbackConnectionConfiguration == null) {
			throw new NullPointerException("Feedback connection configuration must not be null.");
		}

		this.feedbackConfiguration = feedbackConnectionConfiguration;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + concurrentConnectionCount;
		result = prime
				* result
				+ ((connectionConfiguration == null) ? 0
						: connectionConfiguration.hashCode());
		result = prime
				* result
				+ ((feedbackConfiguration == null) ? 0 : feedbackConfiguration
						.hashCode());
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
		final PushManagerConfiguration other = (PushManagerConfiguration) obj;
		if (concurrentConnectionCount != other.concurrentConnectionCount)
			return false;
		if (connectionConfiguration == null) {
			if (other.connectionConfiguration != null)
				return false;
		} else if (!connectionConfiguration
				.equals(other.connectionConfiguration))
			return false;
		if (feedbackConfiguration == null) {
			if (other.feedbackConfiguration != null)
				return false;
		} else if (!feedbackConfiguration.equals(other.feedbackConfiguration))
			return false;
		return true;
	}
}
