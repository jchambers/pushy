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
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class PushManagerTest extends BasePushyTest {
	
	private class TestListener implements RejectedNotificationListener<SimpleApnsPushNotification> {
		
		private final AtomicInteger rejectedNotificationCount = new AtomicInteger(0);

		public void handleRejectedNotification(final SimpleApnsPushNotification notification, final RejectedNotificationReason reason) {
			this.rejectedNotificationCount.incrementAndGet();
		}
		
		public int getRejectedNotificationCount() {
			return this.rejectedNotificationCount.get();
		}
	}

	@Test
	public void testRegisterRejectedNotificationListener() throws InterruptedException {
		final SimpleApnsPushNotification notification = this.createTestNotification();
		
		final TestListener listener = new TestListener();
		this.getPushManager().registerRejectedNotificationListener(listener);
		
		assertEquals(0, listener.getRejectedNotificationCount());
		
		final int iterations = 100;
		this.getServer().failWithErrorAfterNotifications(RejectedNotificationReason.INVALID_TOKEN, 10);
		final CountDownLatch latch = this.getServer().getCountDownLatch(iterations);
		
		for (int i = 0; i < iterations; i++) {
			this.getPushManager().enqueuePushNotification(notification);
		}
		
		this.waitForLatch(latch);
		
		assertEquals(1, listener.getRejectedNotificationCount());
	}
	
	@Test
	public void testShutdown() throws InterruptedException {
		{
			final PushManager<ApnsPushNotification> defaultGroupPushManager =
					new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, null, null);
			
			defaultGroupPushManager.start();
			defaultGroupPushManager.shutdown();
			
			assertTrue(defaultGroupPushManager.getWorkerGroup().isShutdown());
		}
		
		{
			final NioEventLoopGroup group = new NioEventLoopGroup(1);
			
			final PushManager<ApnsPushNotification> providedGroupPushManager =
					new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, null, null, 1, group);
			
			providedGroupPushManager.start();
			providedGroupPushManager.shutdown();
			
			assertEquals(group, providedGroupPushManager.getWorkerGroup());
			assertFalse(group.isShutdown());

			group.shutdownGracefully();
		}
	}
	
	@Test(expected = IllegalStateException.class)
	public void testDoubleStart() {
		final PushManager<ApnsPushNotification> doubleStartPushManager =
				new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, null, null);
		
		doubleStartPushManager.start();
		doubleStartPushManager.start();
	}
	
	@Test(expected = IllegalStateException.class)
	public void testPrematureShutdown() throws InterruptedException {
		final PushManager<ApnsPushNotification> prematureShutdownPushManager =
				new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, null, null);
		
		prematureShutdownPushManager.shutdown();
	}
	
	@Test
	public void testRepeatedShutdown() throws InterruptedException {
		final PushManager<ApnsPushNotification> repeatedShutdownPushManager =
				new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, null, null);
		
		repeatedShutdownPushManager.start();
		repeatedShutdownPushManager.shutdown();
		repeatedShutdownPushManager.shutdown();
	}
	
	@Test
	public void testGetExpiredTokens() throws InterruptedException {
		assertTrue(this.getPushManager().getExpiredTokens().isEmpty());
	}
	
	@Test(expected = IllegalStateException.class)
	public void testGetExpiredTokensBeforeStart() throws InterruptedException {
		final PushManager<ApnsPushNotification> unstartedPushManager =
				new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, null, null);
		
		unstartedPushManager.getExpiredTokens();
	}
	
	@Test(expected = IllegalStateException.class)
	public void testGetExpiredTokensAfterShutdown() throws InterruptedException {
		final PushManager<ApnsPushNotification> shutDownPushManager =
				new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, null, null);
		
		shutDownPushManager.start();
		shutDownPushManager.shutdown();
		
		shutDownPushManager.getExpiredTokens();
	}
	
	@Test
	public void testIsStarted() throws InterruptedException {
		final PushManager<ApnsPushNotification> testPushManager =
				new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, null, null);
		
		assertFalse(testPushManager.isStarted());
		
		testPushManager.start();
		assertTrue(testPushManager.isStarted());
		
		testPushManager.shutdown();
		assertFalse(testPushManager.isStarted());
	}
	
	@Test
	public void testIsShutDown() throws InterruptedException {
		final PushManager<ApnsPushNotification> testPushManager =
				new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, null, null);
		
		assertFalse(testPushManager.isShutDown());
		
		testPushManager.start();
		assertFalse(testPushManager.isShutDown());
		
		testPushManager.shutdown();
		assertTrue(testPushManager.isShutDown());
	}
	
	@Test
	public void testDrain() throws InterruptedException {
		final PushManager<ApnsPushNotification> testPushManager =
				new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, null, null);
		int iterations = 200;
		testPushManager.start();
		final CountDownLatch latch = this.getServer().getCountDownLatch(iterations);

		ApnsPushNotification notification = createTestNotification();
		for(int i=0;i<iterations;i++) {
			testPushManager.enqueuePushNotification(notification);
		}
		
		testPushManager.setAcceptingMore(false);
		testPushManager.drainAndShutdown(1);
		latch.await(1, TimeUnit.MILLISECONDS);
	}
}
