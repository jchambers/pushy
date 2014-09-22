package com.relayrides.pushy.apns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FeedbackConnectionConfigurationTest {

	@Test
	public void testFeedbackConnectionConfiguration() {
		final FeedbackConnectionConfiguration configuration = new FeedbackConnectionConfiguration();

		assertTrue(configuration.getReadTimeout() > 0);
	}

	@Test
	public void testFeedbackConnectionConfigurationFeedbackConnectionConfiguration() {
		final FeedbackConnectionConfiguration configuration = new FeedbackConnectionConfiguration();
		configuration.setReadTimeout(17);

		final FeedbackConnectionConfiguration configurationCopy = new FeedbackConnectionConfiguration(configuration);

		assertEquals(configuration, configurationCopy);
	}

	@Test
	public void testSetReadTimeout() {
		final int expectedTimeout = 17;

		final FeedbackConnectionConfiguration configuration = new FeedbackConnectionConfiguration();
		configuration.setReadTimeout(expectedTimeout);

		assertEquals(expectedTimeout, configuration.getReadTimeout());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetReadTimeoutNonPositive() {
		new FeedbackConnectionConfiguration().setReadTimeout(0);
	}
}
