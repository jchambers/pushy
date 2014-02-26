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
import static org.junit.Assert.assertTrue;
import io.netty.channel.nio.NioEventLoopGroup;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;

import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class PushManagerTest extends BasePushyTest {

	private class TestRejectedNotificationListener implements RejectedNotificationListener<SimpleApnsPushNotification> {

		private final AtomicInteger rejectedNotificationCount = new AtomicInteger(0);

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

		public void handleFailedConnection(final PushManager<? extends SimpleApnsPushNotification> pushManager, final Throwable cause) {
			this.pushManager = pushManager;
			this.cause = cause;

			synchronized (this.mutex) {
				this.mutex.notifyAll();
			}
		}
	}

	@Test
	public void testRegisterRejectedNotificationListener() throws InterruptedException {
		final SimpleApnsPushNotification notification = this.createTestNotification();

		final TestRejectedNotificationListener listener = new TestRejectedNotificationListener();
		this.getPushManager().registerRejectedNotificationListener(listener);

		assertEquals(0, listener.getRejectedNotificationCount());

		final int iterations = 100;
		this.getApnsServer().failWithErrorAfterNotifications(RejectedNotificationReason.INVALID_TOKEN, 10);
		final CountDownLatch latch = this.getApnsServer().getCountDownLatch(iterations);

		for (int i = 0; i < iterations; i++) {
			this.getPushManager().getQueue().put(notification);
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
	public void testRegisterFailedNotificationListener() throws Exception {

		final PushManagerFactory<SimpleApnsPushNotification> factory =
				new PushManagerFactory<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient("/pushy-test-client-untrusted.jks"));

		final PushManager<SimpleApnsPushNotification> badCredentialManager = factory.buildPushManager();

		final Object mutex = new Object();
		final TestFailedConnectionListener listener = new TestFailedConnectionListener(mutex);

		badCredentialManager.registerFailedConnectionListener(listener);

		synchronized (mutex) {
			badCredentialManager.start();
			mutex.wait();
		}

		badCredentialManager.shutdown();

		assertEquals(badCredentialManager, listener.pushManager);
		assertTrue(listener.cause instanceof SSLHandshakeException);
	}

	@Test
	public void testUnregisterFailedNotificationListener() {
		final TestFailedConnectionListener listener = new TestFailedConnectionListener(null);

		this.getPushManager().registerFailedConnectionListener(listener);

		assertTrue(this.getPushManager().unregisterFailedConnectionListener(listener));
		assertFalse(this.getPushManager().unregisterFailedConnectionListener(listener));
	}

	@Test
	public void testShutdown() throws InterruptedException, KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {
		{
			final PushManager<ApnsPushNotification> defaultGroupPushManager =
					new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), 1, null, null, null);

			defaultGroupPushManager.start();
			defaultGroupPushManager.shutdown();

			assertTrue(defaultGroupPushManager.isShutDown());
		}

		{
			final NioEventLoopGroup group = new NioEventLoopGroup(1);

			final PushManager<ApnsPushNotification> providedGroupPushManager =
					new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), 1, group, null, null);

			providedGroupPushManager.start();
			providedGroupPushManager.shutdown();

			assertTrue(providedGroupPushManager.isShutDown());
			assertFalse(group.isShutdown());

			group.shutdownGracefully();
		}

		{
			final ExecutorService listenerExecutorService = Executors.newSingleThreadExecutor();

			final PushManager<ApnsPushNotification> providedExecutorServicePushManager =
					new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), 1, null, listenerExecutorService, null);

			providedExecutorServicePushManager.start();
			providedExecutorServicePushManager.shutdown();

			assertTrue(providedExecutorServicePushManager.isShutDown());
			assertFalse(listenerExecutorService.isShutdown());

			listenerExecutorService.shutdown();
		}
	}

	@Test(timeout = 10000)
	public void testDrainBeforeShutdown() throws InterruptedException {
		final int iterations = 1000;
		final ArrayList<SimpleApnsPushNotification> notificationsToSend = new ArrayList<SimpleApnsPushNotification>(iterations);

		for (int i = 0; i < iterations; i++) {
			notificationsToSend.add(this.createTestNotification());
		}

		this.getApnsServer().failWithErrorAfterNotifications(RejectedNotificationReason.PROCESSING_ERROR, iterations / 2);

		this.getPushManager().start();

		// Hacky: wait for a non-empty connection pool
		Thread.sleep(1000);

		this.getPushManager().getRetryQueue().addAll(notificationsToSend);
		this.getPushManager().shutdown();

		assertEquals(iterations, this.getApnsServer().getReceivedNotifications().size());
		assertTrue(this.getPushManager().getRetryQueue().isEmpty());
	}

	@Test(expected = IllegalStateException.class)
	public void testDoubleStart() throws KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {
		final PushManager<ApnsPushNotification> doubleStartPushManager =
				new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), 1, null, null, null);

		doubleStartPushManager.start();
		doubleStartPushManager.start();
	}

	@Test(expected = IllegalStateException.class)
	public void testPrematureShutdown() throws InterruptedException, KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {
		final PushManager<ApnsPushNotification> prematureShutdownPushManager =
				new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), 1, null, null, null);

		prematureShutdownPushManager.shutdown();
	}

	@Test
	public void testRepeatedShutdown() throws InterruptedException, KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {
		final PushManager<ApnsPushNotification> repeatedShutdownPushManager =
				new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), 1, null, null, null);

		repeatedShutdownPushManager.start();
		repeatedShutdownPushManager.shutdown();
		repeatedShutdownPushManager.shutdown();
	}

	@Test
	public void testGetExpiredTokens() throws InterruptedException, FeedbackConnectionException {
		this.getPushManager().start();
		assertTrue(this.getPushManager().getExpiredTokens().isEmpty());
		this.getPushManager().shutdown();
	}

	@Test(expected = IllegalStateException.class)
	public void testGetExpiredTokensBeforeStart() throws InterruptedException, FeedbackConnectionException {
		this.getPushManager().getExpiredTokens();
	}

	@Test(expected = IllegalStateException.class)
	public void testGetExpiredTokensAfterShutdown() throws InterruptedException, FeedbackConnectionException {
		this.getPushManager().start();
		this.getPushManager().shutdown();

		this.getPushManager().getExpiredTokens();
	}

	@Test
	public void testIsStarted() throws InterruptedException, KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {
		assertFalse(this.getPushManager().isStarted());

		this.getPushManager().start();
		assertTrue(this.getPushManager().isStarted());

		this.getPushManager().shutdown();
		assertFalse(this.getPushManager().isStarted());
	}

	@Test
	public void testIsShutDown() throws InterruptedException, KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {
		assertFalse(this.getPushManager().isShutDown());

		this.getPushManager().start();
		assertFalse(this.getPushManager().isShutDown());

		this.getPushManager().shutdown();
		assertTrue(this.getPushManager().isShutDown());
	}

	@Test
	public void testSendNotifications() throws InterruptedException {
		final int iterations = 1000;

		final CountDownLatch latch = this.getApnsServer().getCountDownLatch(iterations);

		for (int i = 0; i < iterations; i++) {
			this.getPushManager().getQueue().add(this.createTestNotification());
		}

		this.getPushManager().start();
		this.waitForLatch(latch);
		this.getPushManager().shutdown();

		assertEquals(iterations, this.getApnsServer().getReceivedNotifications().size());
	}

	@Test
	public void testSendNotificationsWithError() throws InterruptedException {
		final int iterations = 1000;

		final CountDownLatch latch = this.getApnsServer().getCountDownLatch(iterations);

		for (int i = 0; i < iterations; i++) {
			this.getPushManager().getQueue().add(this.createTestNotification());
		}

		this.getApnsServer().failWithErrorAfterNotifications(RejectedNotificationReason.PROCESSING_ERROR, iterations / 2);

		this.getPushManager().start();
		this.waitForLatch(latch);
		this.getPushManager().shutdown();

		assertEquals(iterations, this.getApnsServer().getReceivedNotifications().size());
	}

	@Test
	public void testSendNotificationsWithParallelConnections() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final PushManagerFactory<SimpleApnsPushNotification> factory =
				new PushManagerFactory<SimpleApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient());

		factory.setEventLoopGroup(this.getEventLoopGroup());
		factory.setConcurrentConnectionCount(4);

		final PushManager<SimpleApnsPushNotification> parallelPushManager = factory.buildPushManager();

		final int iterations = 1000;

		final CountDownLatch latch = this.getApnsServer().getCountDownLatch(iterations);

		for (int i = 0; i < iterations; i++) {
			parallelPushManager.getQueue().add(this.createTestNotification());
		}

		parallelPushManager.start();
		this.waitForLatch(latch);
		parallelPushManager.shutdown();

		assertEquals(iterations, this.getApnsServer().getReceivedNotifications().size());
	}

	@Test
	public void testSendNotificationsWithParallelConnectionsAndError() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final PushManagerFactory<SimpleApnsPushNotification> factory =
				new PushManagerFactory<SimpleApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient());

		factory.setEventLoopGroup(this.getEventLoopGroup());
		factory.setConcurrentConnectionCount(4);

		final PushManager<SimpleApnsPushNotification> parallelPushManager = factory.buildPushManager();

		final int iterations = 1000;

		final CountDownLatch latch = this.getApnsServer().getCountDownLatch(iterations);

		for (int i = 0; i < iterations; i++) {
			parallelPushManager.getQueue().add(this.createTestNotification());
		}

		this.getApnsServer().failWithErrorAfterNotifications(RejectedNotificationReason.PROCESSING_ERROR, iterations / 2);

		parallelPushManager.start();
		this.waitForLatch(latch);
		parallelPushManager.shutdown();

		assertEquals(iterations, this.getApnsServer().getReceivedNotifications().size());
	}

	@Test
	public void testHandleDispatchThreadException() throws InterruptedException, KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {

		class PushManagerWithSelfDestructingDispatchThread extends PushManager<SimpleApnsPushNotification> {

			private final CountDownLatch latch;

			protected PushManagerWithSelfDestructingDispatchThread(
					ApnsEnvironment environment, SSLContext sslContext,
					int concurrentConnectionCount,
					NioEventLoopGroup eventLoopGroup,
					BlockingQueue<SimpleApnsPushNotification> queue,
					CountDownLatch latch) {

				super(environment, sslContext, concurrentConnectionCount, eventLoopGroup, null, queue);

				this.latch = latch;
			}

			@Override
			protected Thread createDispatchThread() {
				this.latch.countDown();

				return new Thread(new Runnable() {

					public void run() {
						throw new RuntimeException("This is a test of thread replacement; please DO NOT report this as a bug.");
					}

				});
			}
		}

		// We want to make sure at least two threads get created: one for the initial start, and then one replacement
		final CountDownLatch latch = new CountDownLatch(2);

		final PushManagerWithSelfDestructingDispatchThread testManager =
				new PushManagerWithSelfDestructingDispatchThread(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), 1, this.getEventLoopGroup(),
						new LinkedBlockingQueue<SimpleApnsPushNotification>(), latch);

		testManager.start();
		this.waitForLatch(latch);

		// We want a really fast timeout here because our self-destructing thread won't actually close the connections
		// we create at startup.
		testManager.shutdown(1);
	}
}
