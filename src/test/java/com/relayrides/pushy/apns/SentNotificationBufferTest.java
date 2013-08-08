package com.relayrides.pushy.apns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import com.relayrides.pushy.util.SimpleApnsPushNotification;

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
		
		assertNotNull(this.buffer.getAndRemoveNotificationWithId(0));
		assertNull(this.buffer.getAndRemoveNotificationWithId(0));
	}

	@Test
	public void testGetAndRemoveAllNotificationsAfterId() {
		for (int i = 0; i < CAPACITY + 100; i++) {
			final SendableApnsPushNotification<SimpleApnsPushNotification> sendableNotification =
					new SendableApnsPushNotification<SimpleApnsPushNotification>(this.testNotification, i);
			
			this.buffer.addSentNotification(sendableNotification);
		}
		
		assertEquals(199, this.buffer.getAndRemoveAllNotificationsAfterId(CAPACITY - 100).size());
		assertTrue(this.buffer.getAndRemoveAllNotificationsAfterId(CAPACITY - 100).isEmpty());
	}

}
