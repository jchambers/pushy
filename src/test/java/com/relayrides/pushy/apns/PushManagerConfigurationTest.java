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
