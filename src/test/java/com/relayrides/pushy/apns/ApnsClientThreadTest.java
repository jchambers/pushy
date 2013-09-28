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

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class ApnsClientThreadTest extends BasePushyTest {
	
	@Test
	public void testSendNotification() throws InterruptedException {
		
		final SimpleApnsPushNotification notification = this.createTestNotification();
		
		final CountDownLatch latch = this.getServer().getCountDownLatch(1);
		this.getPushManager().enqueuePushNotification(notification);
		
		this.waitForLatch(latch);
		
		final List<SimpleApnsPushNotification> receivedNotifications = this.getServer().getReceivedNotifications();
		
		assertEquals(1, receivedNotifications.size());
		assertEquals(notification, receivedNotifications.get(0));
	}
	
	@Test
	public void testSendManyNotifications() throws InterruptedException {
		final SimpleApnsPushNotification notification = this.createTestNotification();
		
		final int iterations = 1000;
		final CountDownLatch latch = this.getServer().getCountDownLatch(iterations);
		
		for (int i = 0; i < iterations; i++) {
			this.getPushManager().enqueuePushNotification(notification);
		}
		
		this.waitForLatch(latch);
		
		final List<SimpleApnsPushNotification> receivedNotifications = this.getServer().getReceivedNotifications();
		
		assertEquals(iterations, receivedNotifications.size());
	}
	
	@Test
	public void testSendManyNotificationsWithMultipleThreads() throws InterruptedException {
		final ApnsClientThread<SimpleApnsPushNotification> secondClientThread =
				new ApnsClientThread<SimpleApnsPushNotification>(this.getPushManager());
		
		try {
			secondClientThread.start();
			
			final SimpleApnsPushNotification notification = this.createTestNotification();
			
			final int iterations = 1000;
			final CountDownLatch latch = this.getServer().getCountDownLatch(iterations);
			
			for (int i = 0; i < iterations; i++) {
				this.getPushManager().enqueuePushNotification(notification);
			}
			
			this.waitForLatch(latch);
			
			final List<SimpleApnsPushNotification> receivedNotifications = this.getServer().getReceivedNotifications();
			
			assertEquals(iterations, receivedNotifications.size());
		} finally {
			secondClientThread.requestShutdown();
		}
	}
	
	@Test
	public void testSendNotificationsWithError() throws InterruptedException {
		final SimpleApnsPushNotification notification = this.createTestNotification();
		
		final int iterations = 100;
		this.getServer().failWithErrorAfterNotifications(RejectedNotificationReason.INVALID_TOKEN, 10);
		final CountDownLatch latch = this.getServer().getCountDownLatch(iterations);
		
		for (int i = 0; i < iterations; i++) {
			this.getPushManager().enqueuePushNotification(notification);
		}
		
		this.waitForLatch(latch);
		
		final List<SimpleApnsPushNotification> receivedNotifications = this.getServer().getReceivedNotifications();
		
		assertEquals(iterations, receivedNotifications.size());
	}
	
	@Test
	public void testSendManyNotificationsWithMultipleThreadsAndError() throws InterruptedException {
		final ApnsClientThread<SimpleApnsPushNotification> secondClientThread =
				new ApnsClientThread<SimpleApnsPushNotification>(this.getPushManager());
		
		try {
			secondClientThread.start();
			
			final SimpleApnsPushNotification notification = this.createTestNotification();
			
			final int iterations = 100;
			this.getServer().failWithErrorAfterNotifications(RejectedNotificationReason.INVALID_TOKEN, 10);
			final CountDownLatch latch = this.getServer().getCountDownLatch(iterations);
			
			for (int i = 0; i < iterations; i++) {
				this.getPushManager().enqueuePushNotification(notification);
			}
			
			this.waitForLatch(latch);
			
			final List<SimpleApnsPushNotification> receivedNotifications = this.getServer().getReceivedNotifications();
			
			assertEquals(iterations, receivedNotifications.size());
		} finally {
			secondClientThread.requestShutdown();
		}
	}
	
	@Test
	public void testShutdown() throws InterruptedException {
		final SimpleApnsPushNotification notification = this.createTestNotification();
		
		final int iterations = 100;
		final CountDownLatch latch = this.getServer().getCountDownLatch(iterations);
		
		for (int i = 0; i < iterations; i++) {
			this.getPushManager().enqueuePushNotification(notification);
		}
		
		this.waitForLatch(latch);
		this.getClientThread().requestShutdown();
		
		for (int i = 0; i < iterations; i++) {
			this.getPushManager().enqueuePushNotification(notification);
		}
		
		assertEquals(
				2 * iterations,
				this.getPushManager().shutdown().size() + this.getServer().getReceivedNotifications().size());
	}
}
