package com.relayrides.pushy.apns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PushManagerConfigurationTest {

	@Test
	public void testPushManagerConfiguration() {
		final PushManagerConfiguration configuration = new PushManagerConfiguration();

		assertTrue(configuration.getConcurrentConnectionCount() > 0);
		assertNotNull(configuration.getConnectionConfiguration());
		assertNotNull(configuration.getFeedbackConnectionConfiguration());
	}

	@Test
	public void testPushManagerConfigurationPushManagerConfiguration() {
		final PushManagerConfiguration configuration = new PushManagerConfiguration();
		configuration.setConcurrentConnectionCount(7);
		configuration.setConnectionConfiguration(new ApnsConnectionConfiguration());
		configuration.setFeedbackConnectionConfiguration(new FeedbackConnectionConfiguration());

		final PushManagerConfiguration configurationCopy = new PushManagerConfiguration(configuration);

		assertEquals(configuration, configurationCopy);
	}
}
