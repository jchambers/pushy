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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + concurrentConnectionCount;
		result = prime * result + ((connectionConfiguration == null) ? 0 : connectionConfiguration.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PushManagerConfiguration other = (PushManagerConfiguration) obj;
		if (concurrentConnectionCount != other.concurrentConnectionCount)
			return false;
		if (connectionConfiguration == null) {
			if (other.connectionConfiguration != null)
				return false;
		} else if (!connectionConfiguration.equals(other.connectionConfiguration))
			return false;
		return true;
	}
}
