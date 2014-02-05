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
import static org.junit.Assert.fail;
import io.netty.channel.nio.NioEventLoopGroup;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class PushManagerTest {

	protected static final ApnsEnvironment TEST_ENVIRONMENT =
			new ApnsEnvironment("127.0.0.1", 2195, "127.0.0.1", 2196);

	private static final byte[] TOKEN = new byte[] { 0x12, 0x34, 0x56 };
	private static final String PAYLOAD = "{\"aps\":{\"alert\":\"Hello\"}}";

	private static final long LATCH_TIMEOUT_VALUE = 2;
	private static final TimeUnit LATCH_TIMEOUT_UNIT = TimeUnit.SECONDS;

	private MockApnsServer apnsServer;
	private MockFeedbackServer feedbackServer;

	private NioEventLoopGroup workerGroup;
	private PushManager<SimpleApnsPushNotification> pushManager;

	private class TestListener implements RejectedNotificationListener<SimpleApnsPushNotification> {

		private final AtomicInteger rejectedNotificationCount = new AtomicInteger(0);

		public void handleRejectedNotification(final SimpleApnsPushNotification notification, final RejectedNotificationReason reason) {
			this.rejectedNotificationCount.incrementAndGet();
		}

		public int getRejectedNotificationCount() {
			return this.rejectedNotificationCount.get();
		}
	}

	@Before
	public void setUp() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		this.apnsServer = new MockApnsServer(TEST_ENVIRONMENT.getApnsGatewayPort());
		this.apnsServer.start();

		this.feedbackServer = new MockFeedbackServer(TEST_ENVIRONMENT.getFeedbackPort());
		this.feedbackServer.start();

		this.workerGroup = new NioEventLoopGroup();
		this.pushManager = new PushManager<SimpleApnsPushNotification>(
				TEST_ENVIRONMENT, SSLUtil.createSSLContextForTestClient(), 1, this.workerGroup,
				new LinkedBlockingQueue<SimpleApnsPushNotification>());
	}

	@After
	public void tearDown() throws InterruptedException {
		this.apnsServer.shutdown();
		this.feedbackServer.shutdown();
		this.workerGroup.shutdownGracefully().await();
	}

	@Test
	public void testRegisterRejectedNotificationListener() throws InterruptedException {
		final SimpleApnsPushNotification notification = this.createTestNotification();

		final TestListener listener = new TestListener();
		this.pushManager.registerRejectedNotificationListener(listener);

		assertEquals(0, listener.getRejectedNotificationCount());

		final int iterations = 100;
		this.apnsServer.failWithErrorAfterNotifications(RejectedNotificationReason.INVALID_TOKEN, 10);
		final CountDownLatch latch = this.apnsServer.getCountDownLatch(iterations);

		for (int i = 0; i < iterations; i++) {
			this.pushManager.getQueue().put(notification);
		}

		this.pushManager.start();

		this.waitForLatch(latch);
		assertEquals(1, listener.getRejectedNotificationCount());

		this.pushManager.shutdown();
	}

	@Test
	public void testUnregisterRejectedNotificationListener() {
		final TestListener listener = new TestListener();

		this.pushManager.registerRejectedNotificationListener(listener);

		assertTrue(this.pushManager.unregisterRejectedNotificationListener(listener));
		assertFalse(this.pushManager.unregisterRejectedNotificationListener(listener));
	}

	@Test
	public void testShutdown() throws InterruptedException, KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {
		{
			final PushManager<ApnsPushNotification> defaultGroupPushManager =
					new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, SSLUtil.createSSLContextForTestClient(), 1, null, null);

			defaultGroupPushManager.start();
			defaultGroupPushManager.shutdown();

			assertTrue(defaultGroupPushManager.isShutDown());
		}

		{
			final NioEventLoopGroup group = new NioEventLoopGroup(1);

			final PushManager<ApnsPushNotification> providedGroupPushManager =
					new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, SSLUtil.createSSLContextForTestClient(), 1, group, null);

			providedGroupPushManager.start();
			providedGroupPushManager.shutdown();

			assertTrue(providedGroupPushManager.isShutDown());
			assertFalse(group.isShutdown());

			group.shutdownGracefully();
		}
	}

	@Test(expected = IllegalStateException.class)
	public void testDoubleStart() throws KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {
		final PushManager<ApnsPushNotification> doubleStartPushManager =
				new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, SSLUtil.createSSLContextForTestClient(), 1, null, null);

		doubleStartPushManager.start();
		doubleStartPushManager.start();
	}

	@Test(expected = IllegalStateException.class)
	public void testPrematureShutdown() throws InterruptedException, KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {
		final PushManager<ApnsPushNotification> prematureShutdownPushManager =
				new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, SSLUtil.createSSLContextForTestClient(), 1, null, null);

		prematureShutdownPushManager.shutdown();
	}

	@Test
	public void testRepeatedShutdown() throws InterruptedException, KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {
		final PushManager<ApnsPushNotification> repeatedShutdownPushManager =
				new PushManager<ApnsPushNotification>(TEST_ENVIRONMENT, SSLUtil.createSSLContextForTestClient(), 1, null, null);

		repeatedShutdownPushManager.start();
		repeatedShutdownPushManager.shutdown();
		repeatedShutdownPushManager.shutdown();
	}

	@Test
	public void testGetExpiredTokens() throws InterruptedException, FeedbackConnectionException {
		this.pushManager.start();
		assertTrue(this.pushManager.getExpiredTokens().isEmpty());
		this.pushManager.shutdown();
	}

	@Test(expected = IllegalStateException.class)
	public void testGetExpiredTokensBeforeStart() throws InterruptedException, FeedbackConnectionException {
		this.pushManager.getExpiredTokens();
	}

	@Test(expected = IllegalStateException.class)
	public void testGetExpiredTokensAfterShutdown() throws InterruptedException, FeedbackConnectionException {
		this.pushManager.start();
		this.pushManager.shutdown();

		this.pushManager.getExpiredTokens();
	}

	@Test
	public void testIsStarted() throws InterruptedException, KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {
		assertFalse(this.pushManager.isStarted());

		this.pushManager.start();
		assertTrue(this.pushManager.isStarted());

		this.pushManager.shutdown();
		assertFalse(this.pushManager.isStarted());
	}

	@Test
	public void testIsShutDown() throws InterruptedException, KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {
		assertFalse(this.pushManager.isShutDown());

		this.pushManager.start();
		assertFalse(this.pushManager.isShutDown());

		this.pushManager.shutdown();
		assertTrue(this.pushManager.isShutDown());
	}

	/* @Test
	public void testHandleThreadDeath() throws InterruptedException, KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, IOException {

		final class SelfDestructingApnsClientThread<T extends ApnsPushNotification> extends ApnsClientThread<T> {

			public SelfDestructingApnsClientThread(final PushManager<T> pushManager) {
				super(pushManager);
			}

			@Override
			public void run() {
				throw new RuntimeException("Self-destructing.");
			}
		}

		final class NotifyOnReplacementPushManager<T extends ApnsPushNotification> extends PushManager<T> {

			private final Object mutex;
			private volatile boolean replacedThread = false;

			protected NotifyOnReplacementPushManager(final ApnsEnvironment environment, final SSLContext sslContext,
					final int concurrentConnectionCount, final NioEventLoopGroup workerGroup,
					final BlockingQueue<T> queue, final Object mutex) {

				super(environment, sslContext, concurrentConnectionCount, workerGroup, queue);

				this.mutex = mutex;
			}

			@Override
			protected SelfDestructingApnsClientThread<T> createClientThread() {
				return new SelfDestructingApnsClientThread<T>(this);
			}

			@Override
			protected synchronized void replaceThread(final Thread t) {
				this.replacedThread = true;

				synchronized (this.mutex) {
					this.mutex.notifyAll();
				}
			}

			public boolean didReplaceThread() {
				return this.replacedThread;
			}
		}

		final Object mutex = new Object();
		final NotifyOnReplacementPushManager<ApnsPushNotification> testManager =
				new NotifyOnReplacementPushManager<ApnsPushNotification>(
						TEST_ENVIRONMENT, SSLUtil.createSSLContextForTestClient(), 1, null, null, mutex);

		synchronized (mutex) {
			testManager.start();
			mutex.wait(1000);
		}

		testManager.shutdown();
		assertTrue(testManager.didReplaceThread());
	} */

	private SimpleApnsPushNotification createTestNotification() {
		return new SimpleApnsPushNotification(new byte[] { 0x12, 0x34, 0x56 }, "{\"aps\":{\"alert\":\"Hello\"}}");
	}

	private void waitForLatch(final CountDownLatch latch) throws InterruptedException {
		while (latch.getCount() > 0) {
			if (!latch.await(LATCH_TIMEOUT_VALUE, LATCH_TIMEOUT_UNIT)) {
				fail(String.format("Timed out waiting for latch. Remaining count: %d", latch.getCount()));
			}
		}
	}
}
