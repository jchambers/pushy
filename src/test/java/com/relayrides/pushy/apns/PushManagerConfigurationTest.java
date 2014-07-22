package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import org.junit.Test;

public class PushManagerConfigurationTest {

	@Test
	public void testPushManagerConfiguration() {
		final PushManagerConfiguration configuration = new PushManagerConfiguration();

		assertTrue(configuration.getConcurrentConnectionCount() > 0);
		assertNotNull(configuration.getConnectionConfiguration());
	}

	@Test
	public void testPushManagerConfigurationPushManagerConfiguration() {
		final PushManagerConfiguration configuration = new PushManagerConfiguration();
		configuration.setConcurrentConnectionCount(7);
		configuration.setConnectionConfiguration(new ApnsConnectionConfiguration());

		final PushManagerConfiguration configurationCopy = new PushManagerConfiguration(configuration);

		assertEquals(configuration, configurationCopy);
	}
}
