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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class PushManagerTest extends BasePushyTest {

	private static final String TEST_PUSH_MANAGER_NAME = "Test push manager";

	private class TestRejectedNotificationListener implements RejectedNotificationListener<SimpleApnsPushNotification> {

		private final AtomicInteger rejectedNotificationCount = new AtomicInteger(0);

		@Override
		public void handleRejectedNotification(final PushManager<? extends SimpleApnsPushNotification> pushManager, final SimpleApnsPushNotification notification, final RejectedNotificationReason reason) {
			this.rejectedNotificationCount.incrementAndGet();
		}

		public int getRejectedNotificationCount() {
			return this.rejectedNotificationCount.get();
		}
	}

	private class TestFailedConnectionListener implements FailedConnectionListener<SimpleApnsPushNotification> {

		private final Object mutex;

		private PushManager<? extends SimpleApnsPushNotification> pushManager;
		private Throwable cause;

		public TestFailedConnectionListener(final Object mutex) {
			this.mutex = mutex;
		}

		@Override
		public void handleFailedConnection(final PushManager<? extends SimpleApnsPushNotification> pushManager, final Throwable cause) {
			this.pushManager = pushManager;
			this.cause = cause;

			synchronized (this.mutex) {
				this.mutex.notifyAll();
			}
		}
	}

	private class TestExpiredTokenListener implements ExpiredTokenListener<SimpleApnsPushNotification> {

		private final Object mutex;

		private Collection<ExpiredToken> expiredTokens;

		public TestExpiredTokenListener(final Object mutex) {
			this.mutex = mutex;
		}

		@Override
		public void handleExpiredTokens(final PushManager<? extends SimpleApnsPushNotification> pushManager, final Collection<ExpiredToken> expiredTokens) {

			this.expiredTokens = expiredTokens;

			synchronized (this.mutex) {
				this.mutex.notifyAll();
			}
		}
	}

	@Test
	public void testPushManagerNullName() throws Exception {
		final PushManager<SimpleApnsPushNotification> pushManager = new PushManager<SimpleApnsPushNotification>(null,
				null, this.getApnsConnectionFactory(), this.getFeedbackConnectionFactory(), 1, null);

		assertNotNull(pushManager.getName());
	}

	@Test
	public void testRegisterRejectedNotificationListener() throws InterruptedException {
		final SimpleApnsPushNotification notification = this.createTestNotification();

		final TestRejectedNotificationListener listener = new TestRejectedNotificationListener();
		this.getPushManager().registerRejectedNotificationListener(listener);

		assertEquals(0, listener.getRejectedNotificationCount());

		final int iterations = 100;

		// We expect one less because one notification should be rejected
		final CountDownLatch latch = this.getApnsServer().getAcceptedNotificationCountDownLatch(iterations - 1);

		for (int i = 0; i < iterations; i++) {
			if (i == iterations / 2) {
				this.getPushManager().getQueue().add(
						new SimpleApnsPushNotification(new byte[] {}, "This is a deliberately malformed notification."));
			} else {
				this.getPushManager().getQueue().add(notification);
			}
		}

		this.getPushManager().start();

		this.waitForLatch(latch);
		assertEquals(1, listener.getRejectedNotificationCount());

		this.getPushManager().shutdown();
	}

	@Test
	public void testUnregisterRejectedNotificationListener() {
		final TestRejectedNotificationListener listener = new TestRejectedNotificationListener();

		this.getPushManager().registerRejectedNotificationListener(listener);

		assertTrue(this.getPushManager().unregisterRejectedNotificationListener(listener));
		assertFalse(this.getPushManager().unregisterRejectedNotificationListener(listener));
	}

	@Test
	public void testRegisterFailedConnectionListener() throws Exception {

		final ApnsConnectionFactory<SimpleApnsPushNotification> badCredentialApnsConnectionFactory =
				new DefaultApnsConnectionFactory<SimpleApnsPushNotification>(TEST_ENVIRONMENT,
						SSLTestUtil.createSSLContextForTestClient("/pushy-test-client-untrusted.jks"),
						this.getEventLoopGroup(), "TestConnection");

		final FeedbackServiceConnectionFactory badCredentialFeedbackConnectionFactory =
				new DefaultFeedbackServiceConnectionFactory(TEST_ENVIRONMENT,
						SSLTestUtil.createSSLContextForTestClient("/pushy-test-client-untrusted.jks"),
						this.getEventLoopGroup(), "TestFeedbackConnection");

		final PushManager<SimpleApnsPushNotification> badCredentialManager =
				new PushManager<SimpleApnsPushNotification>(null, null, badCredentialApnsConnectionFactory,
						badCredentialFeedbackConnectionFactory, 1, TEST_PUSH_MANAGER_NAME);

		final Object mutex = new Object();
		final TestFailedConnectionListener listener = new TestFailedConnectionListener(mutex);

		badCredentialManager.registerFailedConnectionListener(listener);

		synchronized (mutex) {
			badCredentialManager.start();

			while (listener.cause == null) {
				mutex.wait();
			}
		}

		badCredentialManager.shutdown();

		assertEquals(badCredentialManager, listener.pushManager);
		assertNotNull(listener.cause);
	}

	@Test
	public void testUnregisterFailedConnectionListener() {
		final TestFailedConnectionListener listener = new TestFailedConnectionListener(null);

		this.getPushManager().registerFailedConnectionListener(listener);

		assertTrue(this.getPushManager().unregisterFailedConnectionListener(listener));
		assertFalse(this.getPushManager().unregisterFailedConnectionListener(listener));
	}

	@Test
	public void testShutdown() throws Exception {
		{
			this.getPushManager().start();
			this.getPushManager().shutdown();

			assertTrue(this.getPushManager().isShutDown());
			assertFalse(this.getEventLoopGroup().isShutdown());
		}

		{
			final ScheduledExecutorService listenerExecutorService = Executors.newSingleThreadScheduledExecutor();

			final PushManager<SimpleApnsPushNotification> providedExecutorServicePushManager =
					new PushManager<SimpleApnsPushNotification>(listenerExecutorService, null,
							this.getApnsConnectionFactory(), this.getFeedbackConnectionFactory(), 1, TEST_PUSH_MANAGER_NAME);

			providedExecutorServicePushManager.start();
			providedExecutorServicePushManager.shutdown();

			assertTrue(providedExecutorServicePushManager.isShutDown());
			assertFalse(listenerExecutorService.isShutdown());

			listenerExecutorService.shutdown();
		}
	}

	@Test
	public void testDrainBeforeShutdown() throws InterruptedException {
		final int iterations = 1000;
		final ArrayList<SimpleApnsPushNotification> notificationsToSend = new ArrayList<SimpleApnsPushNotification>(iterations);

		for (int i = 0; i < iterations; i++) {
			if (i == iterations / 2) {
				notificationsToSend.add(
						new SimpleApnsPushNotification(new byte[] {}, "This is a deliberately malformed notification."));
			} else {
				notificationsToSend.add(this.createTestNotification());
			}
		}

		this.getPushManager().start();

		final CountDownLatch firstNotificationLatch = this.getApnsServer().getAcceptedNotificationCountDownLatch(1);
		this.getPushManager().getQueue().add(this.createTestNotification());
		this.waitForLatch(firstNotificationLatch);

		// We expect one less because one notification should be rejected
		final CountDownLatch retryNotificationLatch = this.getApnsServer().getAcceptedNotificationCountDownLatch(notificationsToSend.size() - 1);
		this.getPushManager().getRetryQueue().addAll(notificationsToSend);
		this.getPushManager().shutdown();

		assertTrue(this.getPushManager().getRetryQueue().isEmpty());
		this.waitForLatch(retryNotificationLatch);
	}

	@Test(expected = IllegalStateException.class)
	public void testDoubleStart() throws Exception {
		this.getPushManager().start();
		this.getPushManager().start();
	}

	@Test(expected = IllegalStateException.class)
	public void testPrematureShutdown() throws Exception {
		this.getPushManager().shutdown();
	}

	@Test
	public void testRepeatedShutdown() throws Exception {
		this.getPushManager().start();
		this.getPushManager().shutdown();
		this.getPushManager().shutdown();
	}

	@Test
	public void testRequestExpiredTokens() throws InterruptedException {
		final Object mutex = new Object();
		final TestExpiredTokenListener listener = new TestExpiredTokenListener(mutex);

		this.getPushManager().registerExpiredTokenListener(listener);
		this.getPushManager().start();

		synchronized (mutex) {
			this.getPushManager().requestExpiredTokens();

			while (listener.expiredTokens == null) {
				mutex.wait();
			}
		}

		assertTrue(listener.expiredTokens.isEmpty());

		this.getPushManager().shutdown();
	}

	@Test
	public void testRequestExpiredTokensWithDefaultEventLoopGroup() throws Exception {
		final Object mutex = new Object();
		final TestExpiredTokenListener listener = new TestExpiredTokenListener(mutex);

		this.getPushManager().registerExpiredTokenListener(listener);
		this.getPushManager().start();

		try {
			synchronized (mutex) {
				this.getPushManager().requestExpiredTokens();

				while (listener.expiredTokens == null) {
					mutex.wait();
				}
			}

			assertTrue(listener.expiredTokens.isEmpty());
		} finally {
			this.getPushManager().shutdown();
		}
	}

	@Test(expected = IllegalStateException.class)
	public void testRequestExpiredTokensBeforeStart() throws InterruptedException {
		this.getPushManager().requestExpiredTokens();
	}

	@Test(expected = IllegalStateException.class)
	public void testRequestExpiredTokensAfterShutdown() throws InterruptedException {
		this.getPushManager().start();
		this.getPushManager().shutdown();

		this.getPushManager().requestExpiredTokens();
	}

	@Test
	public void testIsStarted() throws InterruptedException  {
		assertFalse(this.getPushManager().isStarted());

		this.getPushManager().start();
		assertTrue(this.getPushManager().isStarted());

		this.getPushManager().shutdown();
		assertFalse(this.getPushManager().isStarted());
	}

	@Test
	public void testIsShutDown() throws InterruptedException {
		assertFalse(this.getPushManager().isShutDown());

		this.getPushManager().start();
		assertFalse(this.getPushManager().isShutDown());

		this.getPushManager().shutdown();
		assertTrue(this.getPushManager().isShutDown());
	}

	@Test
	public void testSendNotifications() throws InterruptedException {
		final int iterations = 1000;

		final CountDownLatch latch = this.getApnsServer().getAcceptedNotificationCountDownLatch(iterations);

		for (int i = 0; i < iterations; i++) {
			this.getPushManager().getQueue().add(this.createTestNotification());
		}

		this.getPushManager().start();
		this.waitForLatch(latch);
		this.getPushManager().shutdown();
	}

	@Test
	public void testSendNotificationsWithError() throws InterruptedException {
		final int iterations = 1000;

		// We expect one less because one notification should be rejected
		final CountDownLatch latch = this.getApnsServer().getAcceptedNotificationCountDownLatch(iterations - 1);

		for (int i = 0; i < iterations; i++) {
			if (i == iterations / 2) {
				this.getPushManager().getQueue().add(
						new SimpleApnsPushNotification(new byte[] {}, "This is a deliberately malformed notification."));
			} else {
				this.getPushManager().getQueue().add(this.createTestNotification());
			}
		}

		this.getPushManager().start();
		this.waitForLatch(latch);
		this.getPushManager().shutdown();
	}

	@Test
	public void testSendNotificationsWithParallelConnections() throws Exception {
		final PushManager<SimpleApnsPushNotification> parallelPushManager =
				new PushManager<SimpleApnsPushNotification>(null, null, this.getApnsConnectionFactory(),
						this.getFeedbackConnectionFactory(), 4, TEST_PUSH_MANAGER_NAME);

		final int iterations = 1000;

		final CountDownLatch latch = this.getApnsServer().getAcceptedNotificationCountDownLatch(iterations);

		for (int i = 0; i < iterations; i++) {
			parallelPushManager.getQueue().add(this.createTestNotification());
		}

		parallelPushManager.start();
		this.waitForLatch(latch);
		parallelPushManager.shutdown();
	}

	@Test
	public void testSendNotificationsWithParallelConnectionsAndError() throws Exception {
		final PushManager<SimpleApnsPushNotification> parallelPushManager =
				new PushManager<SimpleApnsPushNotification>(null, null, this.getApnsConnectionFactory(),
						this.getFeedbackConnectionFactory(), 4, TEST_PUSH_MANAGER_NAME);

		final int iterations = 1000;

		// We expect one less because one notification should be rejected
		final CountDownLatch latch = this.getApnsServer().getAcceptedNotificationCountDownLatch(iterations - 1);

		for (int i = 0; i < iterations; i++) {
			if (i == iterations / 2) {
				parallelPushManager.getQueue().add(
						new SimpleApnsPushNotification(new byte[] {}, "This is a deliberately malformed notification."));
			} else {
				parallelPushManager.getQueue().add(this.createTestNotification());
			}
		}

		parallelPushManager.start();
		this.waitForLatch(latch);
		parallelPushManager.shutdown();
	}

	@Test
	public void testHandleDispatchThreadException() throws Exception {

		class PushManagerWithSelfDestructingDispatchThread extends PushManager<SimpleApnsPushNotification> {

			private final CountDownLatch latch;

			protected PushManagerWithSelfDestructingDispatchThread(final CountDownLatch latch) {
				super(null, null, PushManagerTest.this.getApnsConnectionFactory(), PushManagerTest.this.getFeedbackConnectionFactory(), 1, TEST_PUSH_MANAGER_NAME);

				this.latch = latch;
			}

			@Override
			protected Thread createDispatchThread() {
				this.latch.countDown();

				return new Thread(new Runnable() {

					@Override
					public void run() {
						throw new RuntimeException("This is a test of thread replacement; please DO NOT report this as a bug.");
					}

				});
			}
		}

		// We want to make sure at least two threads get created: one for the initial start, and then one replacement
		final CountDownLatch latch = new CountDownLatch(2);

		final PushManagerWithSelfDestructingDispatchThread testManager =
				new PushManagerWithSelfDestructingDispatchThread(latch);

		testManager.start();
		this.waitForLatch(latch);

		// Because the dispatch thread won't be doing its normal job of shutting down connections, we'll want to do a
		// timed shutdown with a very short fuse.
		testManager.shutdown(1);
	}
}
