/* Copyright (c) 2014 RelayRides
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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.SSLContext;

import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class PushNotificationConnectionTest extends ApnsConnectionTest {

	private static final String TEST_CONNECTION_NAME = "TestPushNotificationConnection";

	@Override
	public PushNotificationConnection<SimpleApnsPushNotification> getTestConnection(final ApnsEnvironment environment,
			final SSLContext sslContext, final TestConnectionListener listener) {

		return new PushNotificationConnection<SimpleApnsPushNotification>(environment, sslContext,
				this.getEventLoopGroup(), new PushNotificationConnectionConfiguration(), listener, TEST_CONNECTION_NAME);
	}

	@Test
	public void testApnsConnectionNullListener() throws Exception {
		new PushNotificationConnection<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(),
				this.getEventLoopGroup(), new PushNotificationConnectionConfiguration(), null, TEST_CONNECTION_NAME);
	}

	@Test(expected = NullPointerException.class)
	public void testApnsConnectionNullEnvironment() throws Exception {
		new PushNotificationConnection<ApnsPushNotification>(null, SSLTestUtil.createSSLContextForTestClient(),
				this.getEventLoopGroup(), new PushNotificationConnectionConfiguration(), null, TEST_CONNECTION_NAME);
	}

	@Test(expected = NullPointerException.class)
	public void testApnsConnectionNullSslContext() throws Exception {
		new PushNotificationConnection<ApnsPushNotification>(TEST_ENVIRONMENT, null, this.getEventLoopGroup(),
				new PushNotificationConnectionConfiguration(), null, TEST_CONNECTION_NAME);
	}

	@Test(expected = NullPointerException.class)
	public void testApnsConnectionNullEventLoopGroup() throws Exception {
		new PushNotificationConnection<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(),
				null, new PushNotificationConnectionConfiguration(), null, TEST_CONNECTION_NAME);
	}

	@Test(expected = NullPointerException.class)
	public void testApnsConnectionNullConfiguration() throws Exception {
		new PushNotificationConnection<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(),
				this.getEventLoopGroup(), null, null, TEST_CONNECTION_NAME);
	}

	@Test(expected = NullPointerException.class)
	public void testApnsConnectionNullName() throws Exception {
		new PushNotificationConnection<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(),
				this.getEventLoopGroup(), new PushNotificationConnectionConfiguration(), null, null);
	}

	@Test
	public void testSendNotification() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);
		final PushNotificationConnection<SimpleApnsPushNotification> apnsConnection =
				this.getTestConnection(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), listener);

		final CountDownLatch latch = this.getApnsServer().getAcceptedNotificationCountDownLatch(1);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.hasConnectionSucceeded()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionSucceeded());

		apnsConnection.sendNotification(this.createTestNotification());
		this.waitForLatch(latch);
	}

	@Test
	public void testSendNotificationWithNullPriority() throws Exception {
		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);
		final PushNotificationConnection<SimpleApnsPushNotification> apnsConnection =
				this.getTestConnection(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), listener);

		final CountDownLatch latch = this.getApnsServer().getAcceptedNotificationCountDownLatch(1);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.hasConnectionSucceeded()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionSucceeded());

		final byte[] token = new byte[32];
		new Random().nextBytes(token);

		final SimpleApnsPushNotification nullPriorityNotification = new SimpleApnsPushNotification(
				token, "This is a bogus payload, but that's okay.", null, null);

		apnsConnection.sendNotification(nullPriorityNotification);
		this.waitForLatch(latch);
	}

	@Test
	public void testSendNotificationWithError() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);
		final PushNotificationConnection<SimpleApnsPushNotification> apnsConnection =
				this.getTestConnection(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), listener);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.hasConnectionSucceeded()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionSucceeded());

		final SimpleApnsPushNotification bogusNotification =
				new SimpleApnsPushNotification(new byte[] {}, "This is a bogus notification and should be rejected.");

		synchronized (mutex) {
			apnsConnection.sendNotification(bogusNotification);

			while (!listener.hasConnectionClosed()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionClosed());
		assertEquals(bogusNotification, listener.getRejectedNotification());
		assertEquals(RejectedNotificationReason.MISSING_TOKEN, listener.getRejectionReason());
	}

	@Test
	public void testShutdownGracefully() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);
		final PushNotificationConnection<SimpleApnsPushNotification> apnsConnection =
				this.getTestConnection(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), listener);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.hasConnectionSucceeded()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionSucceeded());

		synchronized (mutex) {
			apnsConnection.shutdownGracefully();

			while (!listener.hasConnectionClosed()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionClosed());
		assertNull(listener.getRejectedNotification());
		assertNull(listener.getRejectionReason());
		assertTrue(listener.getUnprocessedNotifications().isEmpty());
	}

	@Test
	public void testDoubleShutdownGracefully() throws Exception {
		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);
		final PushNotificationConnection<SimpleApnsPushNotification> apnsConnection =
				this.getTestConnection(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), listener);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.hasConnectionSucceeded()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionSucceeded());

		synchronized (mutex) {
			apnsConnection.shutdownGracefully();
			apnsConnection.shutdownGracefully();

			while (!listener.hasConnectionClosed()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionClosed());
		assertNull(listener.getRejectedNotification());
		assertNull(listener.getRejectionReason());
		assertTrue(listener.getUnprocessedNotifications().isEmpty());
	}

	@Test
	public void testShutdownGracefullyBeforeConnect() throws Exception {
		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);
		final PushNotificationConnection<SimpleApnsPushNotification> apnsConnection =
				this.getTestConnection(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), listener);

		apnsConnection.shutdownGracefully();
	}

	@Test
	public void testWaitForPendingOperationsToFinish() throws Exception {
		// For the purposes of this test, we're happy just as long as we don't time out waiting for writes to finish.

		{
			final Object mutex = new Object();

			final TestConnectionListener listener = new TestConnectionListener(mutex);
			final PushNotificationConnection<SimpleApnsPushNotification> apnsConnection =
					this.getTestConnection(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), listener);

			apnsConnection.waitForPendingWritesToFinish();
			apnsConnection.shutdownImmediately();
		}

		{
			final Object mutex = new Object();

			final TestConnectionListener listener = new TestConnectionListener(mutex);
			final PushNotificationConnection<SimpleApnsPushNotification> apnsConnection =
					this.getTestConnection(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), listener);

			synchronized (mutex) {
				apnsConnection.connect();

				while (!listener.hasConnectionSucceeded()) {
					mutex.wait();
				}
			}

			assertTrue(listener.hasConnectionSucceeded());

			for (int i = 0; i < 1000; i++) {
				apnsConnection.sendNotification(this.createTestNotification());
			}

			apnsConnection.waitForPendingWritesToFinish();
			apnsConnection.shutdownGracefully();
		}
	}

	@Test
	public void testWriteTimeout() throws Exception {
		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);

		final PushNotificationConnectionConfiguration writeTimeoutConfiguration = new PushNotificationConnectionConfiguration();
		writeTimeoutConfiguration.setCloseAfterInactivityTime(1);

		final PushNotificationConnection<SimpleApnsPushNotification> apnsConnection =
				new PushNotificationConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						writeTimeoutConfiguration, listener, TEST_CONNECTION_NAME);

		synchronized (mutex) {
			apnsConnection.connect();

			// Do nothing, but wait for the connection to time out due to inactivity
			while (!listener.hasConnectionClosed()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionClosed());
	}

	@Test
	public void testGracefulShutdownTimeout() throws Exception {
		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);

		final PushNotificationConnectionConfiguration gracefulShutdownTimeoutConfiguration = new PushNotificationConnectionConfiguration();
		gracefulShutdownTimeoutConfiguration.setGracefulShutdownTimeout(1);

		final PushNotificationConnection<SimpleApnsPushNotification> apnsConnection =
				new PushNotificationConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						gracefulShutdownTimeoutConfiguration, listener, TEST_CONNECTION_NAME);

		// We'll pretend that we have a "dead" connection; it will be up to the graceful shutdown timeout to close the
		// connection.
		this.getApnsServer().setShouldSendErrorResponses(false);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.hasConnectionSucceeded()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionSucceeded());

		synchronized (mutex) {
			apnsConnection.shutdownGracefully();

			while (!listener.hasConnectionClosed()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionClosed());
	}

	@Test
	public void testShutdownAtSendAttemptLimit() throws Exception {

		final int notificationCount = 1000;
		final int sendAttemptLimit = 100;

		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);

		final PushNotificationConnectionConfiguration sendAttemptLimitConfiguration = new PushNotificationConnectionConfiguration();
		sendAttemptLimitConfiguration.setSendAttemptLimit(100);

		final PushNotificationConnection<SimpleApnsPushNotification> apnsConnection =
				new PushNotificationConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						sendAttemptLimitConfiguration, listener, TEST_CONNECTION_NAME);

		final CountDownLatch totalLatch = this.getApnsServer().getAcceptedNotificationCountDownLatch(notificationCount);
		final CountDownLatch limitLatch = this.getApnsServer().getAcceptedNotificationCountDownLatch(sendAttemptLimit);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.hasConnectionSucceeded()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionSucceeded());

		for (int i = 0; i < notificationCount; i++) {
			apnsConnection.sendNotification(this.createTestNotification());
		}

		this.waitForLatch(limitLatch);
		assertEquals(notificationCount - sendAttemptLimit, totalLatch.getCount());
	}
}
