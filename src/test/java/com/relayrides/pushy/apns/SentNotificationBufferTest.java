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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class SentNotificationBufferTest {

	private SimpleApnsPushNotification testNotification;
	private SentNotificationBuffer<SimpleApnsPushNotification> buffer;

	private static final int CAPACITY = 2048;

	@Before
	public void setUp() throws Exception {
		this.testNotification = new SimpleApnsPushNotification(
				new byte[] { 0x12, 0x34, 0x56, 0x78 },
				"This is an invalid payload, but that's okay.",
				new Date());

		this.buffer = new SentNotificationBuffer<SimpleApnsPushNotification>(CAPACITY);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSentNotificationBufferBadCapacity() {
		new SentNotificationBuffer<SimpleApnsPushNotification>(37);
	}

	@Test
	public void testGetAndRemoveNotificationWithId() {
		final SendableApnsPushNotification<SimpleApnsPushNotification> sendableNotification =
				new SendableApnsPushNotification<SimpleApnsPushNotification>(this.testNotification, 0);

		this.buffer.addSentNotification(sendableNotification);

		assertNotNull(this.buffer.getAndRemoveNotificationWithSequenceNumber(0));
		assertNull(this.buffer.getAndRemoveNotificationWithSequenceNumber(0));
	}

	@Test
	public void testGetAndRemoveAllNotificationsAfterId() {
		for (int i = 0; i < CAPACITY + 100; i++) {
			final SendableApnsPushNotification<SimpleApnsPushNotification> sendableNotification =
					new SendableApnsPushNotification<SimpleApnsPushNotification>(this.testNotification, i);

			this.buffer.addSentNotification(sendableNotification);
		}

		assertEquals(199, this.buffer.getAndRemoveAllNotificationsAfterSequenceNumber(CAPACITY - 100).size());
		assertTrue(this.buffer.getAndRemoveAllNotificationsAfterSequenceNumber(CAPACITY - 100).isEmpty());
	}

}
