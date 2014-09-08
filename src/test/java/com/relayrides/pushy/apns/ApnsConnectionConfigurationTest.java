package com.relayrides.pushy.apns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ApnsConnectionConfigurationTest {

	@Test
	public void testApnsConnectionConfiguration() {
		final ApnsConnectionConfiguration configuration = new ApnsConnectionConfiguration();

		assertTrue(configuration.getSentNotificationBufferCapacity() > 0);
		assertNull(configuration.getCloseAfterInactivityTime());
	}

	@Test
	public void testApnsConnectionConfigurationApnsConnectionConfiguration() {
		final ApnsConnectionConfiguration configuration = new ApnsConnectionConfiguration();
		configuration.setSentNotificationBufferCapacity(17);
		configuration.setCloseAfterInactivityTime(19);

		final ApnsConnectionConfiguration configurationCopy = new ApnsConnectionConfiguration(configuration);

		assertEquals(configuration, configurationCopy);
	}
}
