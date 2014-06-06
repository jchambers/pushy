package com.relayrides.pushy.apns;

/**
 *
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class PushManagerConfiguration {

	private int concurrentConnectionCount = 1;

	private final ApnsConnectionConfiguration connectionConfiguration = new ApnsConnectionConfiguration();

	public PushManagerConfiguration() {}

	public PushManagerConfiguration(final PushManagerConfiguration configuration) {
		this.concurrentConnectionCount = configuration.getConcurrentConnectionCount();
	}

	public int getConcurrentConnectionCount() {
		return concurrentConnectionCount;
	}

	public void setConcurrentConnectionCount(int concurrentConnectionCount) {
		this.concurrentConnectionCount = concurrentConnectionCount;
	}

	public ApnsConnectionConfiguration getConnectionConfiguration() {
		return connectionConfiguration;
	}
}
