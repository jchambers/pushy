package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import org.junit.Test;

public class ApnsConnectionConfigurationTest {

	@Test
	public void testApnsConnectionConfiguration() {
		final ApnsConnectionConfiguration configuration = new ApnsConnectionConfiguration();

		assertTrue(configuration.getSentNotificationBufferCapacity() > 0);
	}

	@Test
	public void testApnsConnectionConfigurationApnsConnectionConfiguration() {
		final ApnsConnectionConfiguration configuration = new ApnsConnectionConfiguration();
		configuration.setSentNotificationBufferCapacity(17);

		final ApnsConnectionConfiguration configurationCopy = new ApnsConnectionConfiguration(configuration);

		assertEquals(configuration, configurationCopy);
	}
}
