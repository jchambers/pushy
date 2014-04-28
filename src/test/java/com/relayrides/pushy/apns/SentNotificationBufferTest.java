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
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class SentNotificationBufferTest {

	private SimpleApnsPushNotification testNotification;

	@Before
	public void setUp() throws Exception {
		this.testNotification = new SimpleApnsPushNotification(
				new byte[] { 0x12, 0x34, 0x56, 0x78 },
				"This is an invalid payload, but that's okay.",
				new Date());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSentNotificationBufferBadCapacity() {
		new SentNotificationBuffer<SimpleApnsPushNotification>(0);
	}

	@Test
	public void testAddSentNotification() {
		final int capacity = 8;

		final SentNotificationBuffer<SimpleApnsPushNotification> buffer =
				new SentNotificationBuffer<SimpleApnsPushNotification>(capacity);

		assertEquals(0, buffer.size());

		for (int i = 0; i < 2 * capacity; i++) {
			buffer.addSentNotification(new SendableApnsPushNotification<SimpleApnsPushNotification>(this.testNotification, i));
		}

		assertEquals(capacity, buffer.size());
	}

	@Test
	public void testClearNotificationsBeforeSequenceNumber() {
		final int capacity = 10;

		{
			final SentNotificationBuffer<SimpleApnsPushNotification> buffer =
					new SentNotificationBuffer<SimpleApnsPushNotification>(capacity);

			final int startingIndex = 0;

			for (final SendableApnsPushNotification<SimpleApnsPushNotification> notification : this.generateSequentialNotifications(capacity, startingIndex)) {
				buffer.addSentNotification(notification);
			}

			buffer.clearNotificationsBeforeSequenceNumber(startingIndex + 5);
			assertEquals(5, buffer.size());
		}

		{
			final SentNotificationBuffer<SimpleApnsPushNotification> buffer =
					new SentNotificationBuffer<SimpleApnsPushNotification>(capacity);

			final int startingIndex = Integer.MAX_VALUE - 2;

			for (final SendableApnsPushNotification<SimpleApnsPushNotification> notification : this.generateSequentialNotifications(capacity, startingIndex)) {
				buffer.addSentNotification(notification);
			}

			buffer.clearNotificationsBeforeSequenceNumber(startingIndex + 5);
			assertEquals(5, buffer.size());
		}
	}

	@Test
	public void testGetNotificationWithSequenceNumber() {
		final int capacity = 21;

		final SentNotificationBuffer<SimpleApnsPushNotification> buffer =
				new SentNotificationBuffer<SimpleApnsPushNotification>(capacity);

		assertNull(buffer.getNotificationWithSequenceNumber(10));

		for (final SendableApnsPushNotification<SimpleApnsPushNotification> notification : this.generateSequentialNotifications(10, 0)) {
			buffer.addSentNotification(notification);
		}

		final SendableApnsPushNotification<SimpleApnsPushNotification> needle =
				new SendableApnsPushNotification<SimpleApnsPushNotification>(new SimpleApnsPushNotification(
						new byte[] { 0x17 }, "This is the notification we're looking for."), 10);

		buffer.addSentNotification(needle);

		for (final SendableApnsPushNotification<SimpleApnsPushNotification> notification : this.generateSequentialNotifications(10, 11)) {
			buffer.addSentNotification(notification);
		}

		assertEquals(needle.getPushNotification(), buffer.getNotificationWithSequenceNumber(10));
	}

	@Test
	public void testGetAllNotificationsAfterSequenceNumber() {
		final int capacity = 10;

		final SentNotificationBuffer<SimpleApnsPushNotification> buffer =
				new SentNotificationBuffer<SimpleApnsPushNotification>(capacity);

		for (final SendableApnsPushNotification<SimpleApnsPushNotification> notification : this.generateSequentialNotifications(capacity, 0)) {
			buffer.addSentNotification(notification);
		}

		assertEquals(2, buffer.getAllNotificationsAfterSequenceNumber(7).size());
	}

	@Test
	public void testClearAllNotifications() {
		final int capacity = 10;

		final SentNotificationBuffer<SimpleApnsPushNotification> buffer =
				new SentNotificationBuffer<SimpleApnsPushNotification>(capacity);

		for (final SendableApnsPushNotification<SimpleApnsPushNotification> notification : this.generateSequentialNotifications(capacity, 0)) {
			buffer.addSentNotification(notification);
		}

		assertEquals(capacity, buffer.size());

		buffer.clearAllNotifications();
		assertEquals(0, buffer.size());
	}

	@Test
	public void testGetSequenceNumbersEmpty() {
		final SentNotificationBuffer<SimpleApnsPushNotification> buffer =
				new SentNotificationBuffer<SimpleApnsPushNotification>(1);

		assertNull(buffer.getLowestSequenceNumber());
		assertNull(buffer.getHighestSequenceNumber());
		assertEquals(0, buffer.size());
	}

	@Test
	public void testGetSequenceNumbersSingleItem() {
		final int sequenceNumber = 17;

		final SentNotificationBuffer<SimpleApnsPushNotification> buffer =
				new SentNotificationBuffer<SimpleApnsPushNotification>(1);

		final SendableApnsPushNotification<SimpleApnsPushNotification> sendableNotification =
				new SendableApnsPushNotification<SimpleApnsPushNotification>(this.testNotification, sequenceNumber);

		buffer.addSentNotification(sendableNotification);

		assertEquals((Integer)sequenceNumber, buffer.getLowestSequenceNumber());
		assertEquals((Integer)sequenceNumber, buffer.getHighestSequenceNumber());
		assertEquals(1, buffer.size());
	}

	@Test
	public void testGetSequenceNumbersMultipleItems() {
		final int capacity = 10;

		final SentNotificationBuffer<SimpleApnsPushNotification> buffer =
				new SentNotificationBuffer<SimpleApnsPushNotification>(capacity);

		for (final SendableApnsPushNotification<SimpleApnsPushNotification> notification : this.generateSequentialNotifications(capacity, 0)) {
			buffer.addSentNotification(notification);
		}

		assertEquals((Integer)0, buffer.getLowestSequenceNumber());
		assertEquals((Integer)(capacity - 1), buffer.getHighestSequenceNumber());
		assertEquals(capacity, buffer.size());
	}

	private List<SendableApnsPushNotification<SimpleApnsPushNotification>> generateSequentialNotifications(final int count, final int startingSequenceNumber) {

		final ArrayList<SendableApnsPushNotification<SimpleApnsPushNotification>> sendableNotifications =
				new ArrayList<SendableApnsPushNotification<SimpleApnsPushNotification>>(count);

		int currentSequenceNumber = startingSequenceNumber;

		// We don't want to use the sequence number as the loop index to work around integer wrapping issues
		for (int i = 0; i < count; i++) {
			sendableNotifications.add(new SendableApnsPushNotification<SimpleApnsPushNotification>(
					this.testNotification, currentSequenceNumber++));
		}

		return sendableNotifications;
	}
}
