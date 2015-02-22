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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.SSLHandshakeException;

import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class ApnsConnectionTest extends BasePushyTest {

	private static final String TEST_CONNECTION_NAME = "Test connection";

	private class TestListener implements ApnsConnectionListener<SimpleApnsPushNotification> {

		private final Object mutex;

		private boolean connectionSucceeded = false;
		private boolean connectionFailed = false;
		private boolean connectionClosed = false;

		private Throwable connectionFailureCause;

		private final ArrayList<SimpleApnsPushNotification> writeFailures = new ArrayList<SimpleApnsPushNotification>();

		private SimpleApnsPushNotification rejectedNotification;
		private RejectedNotificationReason rejectionReason;

		private final ArrayList<SimpleApnsPushNotification> unprocessedNotifications = new ArrayList<SimpleApnsPushNotification>();

		public TestListener(final Object mutex) {
			this.mutex = mutex;
		}

		@Override
		public void handleConnectionSuccess(final ApnsConnection<SimpleApnsPushNotification> connection) {
			synchronized (this.mutex) {
				this.connectionSucceeded = true;
				this.mutex.notifyAll();
			}
		}

		@Override
		public void handleConnectionFailure(final ApnsConnection<SimpleApnsPushNotification> connection, final Throwable cause) {
			synchronized (mutex) {
				this.connectionFailed = true;
				this.connectionFailureCause = cause;

				this.mutex.notifyAll();
			}
		}

		@Override
		public void handleConnectionClosure(final ApnsConnection<SimpleApnsPushNotification> connection) {
			try {
				connection.waitForPendingWritesToFinish();
			} catch (InterruptedException ignored) {
			}

			synchronized (mutex) {
				this.connectionClosed = true;
				this.mutex.notifyAll();
			}
		}

		@Override
		public void handleWriteFailure(final ApnsConnection<SimpleApnsPushNotification> connection,
				final SimpleApnsPushNotification notification, final Throwable cause) {

			this.writeFailures.add(notification);
		}

		@Override
		public void handleRejectedNotification(final ApnsConnection<SimpleApnsPushNotification> connection,
				final SimpleApnsPushNotification rejectedNotification, final RejectedNotificationReason reason) {

			this.rejectedNotification = rejectedNotification;
			this.rejectionReason = reason;
		}

		@Override
		public void handleUnprocessedNotifications(final ApnsConnection<SimpleApnsPushNotification> connection,
				final Collection<SimpleApnsPushNotification> unprocessedNotifications) {

			this.unprocessedNotifications.addAll(unprocessedNotifications);
		}

		@Override
		public void handleConnectionWritabilityChange(final ApnsConnection<SimpleApnsPushNotification> connection, final boolean writable) {
		}
	}

	@Test
	public void testApnsConnectionNullListener() throws Exception {
		new ApnsConnection<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(),
				this.getEventLoopGroup(), new ApnsConnectionConfiguration(), null, TEST_CONNECTION_NAME);
	}

	@Test(expected = NullPointerException.class)
	public void testApnsConnectionNullEnvironment() throws Exception {
		new ApnsConnection<ApnsPushNotification>(null, SSLTestUtil.createSSLContextForTestClient(),
				this.getEventLoopGroup(), new ApnsConnectionConfiguration(), null, TEST_CONNECTION_NAME);
	}

	@Test(expected = NullPointerException.class)
	public void testApnsConnectionNullSslContext() throws Exception {
		new ApnsConnection<ApnsPushNotification>(TEST_ENVIRONMENT, null, this.getEventLoopGroup(),
				new ApnsConnectionConfiguration(), null, TEST_CONNECTION_NAME);
	}

	@Test(expected = NullPointerException.class)
	public void testApnsConnectionNullEventLoopGroup() throws Exception {
		new ApnsConnection<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(),
				null, new ApnsConnectionConfiguration(), null, TEST_CONNECTION_NAME);
	}

	@Test(expected = NullPointerException.class)
	public void testApnsConnectionNullConfiguration() throws Exception {
		new ApnsConnection<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(),
				this.getEventLoopGroup(), null, null, TEST_CONNECTION_NAME);
	}

	@Test(expected = NullPointerException.class)
	public void testApnsConnectionNullName() throws Exception {
		new ApnsConnection<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(),
				this.getEventLoopGroup(), new ApnsConnectionConfiguration(), null, null);
	}

	@Test
	public void testConnect() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		// For this test, we just want to make sure that connection succeeds and nothing explodes.
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.connectionSucceeded) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionSucceeded);
	}

	@Test(expected = IllegalStateException.class)
	public void testDoubleConnect() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						new ApnsConnectionConfiguration(), new TestListener(new Object()), TEST_CONNECTION_NAME);

		apnsConnection.connect();
		apnsConnection.connect();
	}

	@Test
	public void testConnectEmptyKeystore() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {

		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);

		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient("/empty-keystore.jks"),
						this.getEventLoopGroup(), new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.connectionFailed) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionFailed);
		assertTrue(listener.connectionFailureCause instanceof SSLHandshakeException);
	}

	@Test
	public void testConnectUntrustedKeystore() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {

		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);

		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient("/pushy-test-client-untrusted.jks"),
						this.getEventLoopGroup(), new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.connectionFailed) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionFailed);
		assertTrue(listener.connectionFailureCause instanceof SSLHandshakeException);
	}

	@Test
	public void testConnectionRefusal() throws Exception {
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);
		final ApnsEnvironment connectionRefusedEnvironment = new ApnsEnvironment("localhost", 7876, "localhost", 7877);

		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						connectionRefusedEnvironment, SSLTestUtil.createSSLContextForTestClient("/pushy-test-client.jks"),
						this.getEventLoopGroup(), new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.connectionFailed) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionFailed);
	}

	@Test
	public void testSendNotification() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

		final CountDownLatch latch = this.getApnsServer().getAcceptedNotificationCountDownLatch(1);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.connectionSucceeded) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionSucceeded);

		apnsConnection.sendNotification(this.createTestNotification());
		this.waitForLatch(latch);
	}

	@Test
	public void testSendNotificationWithNullPriority() throws Exception {
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

		final CountDownLatch latch = this.getApnsServer().getAcceptedNotificationCountDownLatch(1);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.connectionSucceeded) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionSucceeded);

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

		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.connectionSucceeded) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionSucceeded);

		final SimpleApnsPushNotification bogusNotification =
				new SimpleApnsPushNotification(new byte[] {}, "This is a bogus notification and should be rejected.");

		synchronized (mutex) {
			apnsConnection.sendNotification(bogusNotification);

			while (!listener.connectionClosed) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionClosed);
		assertEquals(bogusNotification, listener.rejectedNotification);
		assertEquals(RejectedNotificationReason.MISSING_TOKEN, listener.rejectionReason);
	}

	@Test
	public void testShutdownGracefully() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.connectionSucceeded) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionSucceeded);

		synchronized (mutex) {
			apnsConnection.shutdownGracefully();

			while (!listener.connectionClosed) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionClosed);
		assertNull(listener.rejectedNotification);
		assertNull(listener.rejectionReason);
		assertTrue(listener.unprocessedNotifications.isEmpty());
	}

	@Test
	public void testDoubleShutdownGracefully() throws Exception {
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.connectionSucceeded) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionSucceeded);

		synchronized (mutex) {
			apnsConnection.shutdownGracefully();
			apnsConnection.shutdownGracefully();

			while (!listener.connectionClosed) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionClosed);
		assertNull(listener.rejectedNotification);
		assertNull(listener.rejectionReason);
		assertTrue(listener.unprocessedNotifications.isEmpty());
	}

	@Test
	public void testShutdownGracefullyBeforeConnect() throws Exception {
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

		apnsConnection.shutdownGracefully();
	}

	@Test
	public void testShutdownImmediately() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.connectionSucceeded) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionSucceeded);

		synchronized (mutex) {
			apnsConnection.shutdownImmediately();

			while (!listener.connectionClosed) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionClosed);
	}

	@Test
	public void testShutdownImmediatelyBeforeConnect() throws Exception {
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

		apnsConnection.shutdownImmediately();
	}

	@Test
	public void testWaitForPendingOperationsToFinish() throws Exception {
		// For the purposes of this test, we're happy just as long as we don't time out waiting for writes to finish.

		{
			final Object mutex = new Object();

			final TestListener listener = new TestListener(mutex);
			final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
					new ApnsConnection<SimpleApnsPushNotification>(
							TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
							new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

			apnsConnection.waitForPendingWritesToFinish();
			apnsConnection.shutdownImmediately();
		}

		{
			final Object mutex = new Object();

			final TestListener listener = new TestListener(mutex);
			final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
					new ApnsConnection<SimpleApnsPushNotification>(
							TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
							new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

			synchronized (mutex) {
				apnsConnection.connect();

				while (!listener.connectionSucceeded) {
					mutex.wait();
				}
			}

			assertTrue(listener.connectionSucceeded);

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

		final TestListener listener = new TestListener(mutex);

		final ApnsConnectionConfiguration writeTimeoutConfiguration = new ApnsConnectionConfiguration();
		writeTimeoutConfiguration.setCloseAfterInactivityTime(1);

		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						writeTimeoutConfiguration, listener, TEST_CONNECTION_NAME);

		synchronized (mutex) {
			apnsConnection.connect();

			// Do nothing, but wait for the connection to time out due to inactivity
			while (!listener.connectionClosed) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionClosed);
	}

	@Test
	public void testGracefulShutdownTimeout() throws Exception {
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);

		final ApnsConnectionConfiguration gracefulShutdownTimeoutConfiguration = new ApnsConnectionConfiguration();
		gracefulShutdownTimeoutConfiguration.setGracefulShutdownTimeout(1);

		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						gracefulShutdownTimeoutConfiguration, listener, TEST_CONNECTION_NAME);

		// We'll pretend that we have a "dead" connection; it will be up to the graceful shutdown timeout to close the
		// connection.
		this.getApnsServer().setShouldSendErrorResponses(false);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.connectionSucceeded) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionSucceeded);

		synchronized (mutex) {
			apnsConnection.shutdownGracefully();

			while (!listener.connectionClosed) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionClosed);
	}

	@Test
	public void testShutdownAtSendAttemptLimit() throws Exception {

		final int notificationCount = 1000;
		final int sendAttemptLimit = 100;

		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);

		final ApnsConnectionConfiguration sendAttemptLimitConfiguration = new ApnsConnectionConfiguration();
		sendAttemptLimitConfiguration.setSendAttemptLimit(100);

		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
						sendAttemptLimitConfiguration, listener, TEST_CONNECTION_NAME);

		final CountDownLatch totalLatch = this.getApnsServer().getAcceptedNotificationCountDownLatch(notificationCount);
		final CountDownLatch limitLatch = this.getApnsServer().getAcceptedNotificationCountDownLatch(sendAttemptLimit);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.connectionSucceeded) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionSucceeded);

		for (int i = 0; i < notificationCount; i++) {
			apnsConnection.sendNotification(this.createTestNotification());
		}

		this.waitForLatch(limitLatch);
		assertEquals(notificationCount - sendAttemptLimit, totalLatch.getCount());
	}

	@Test
	public void testHandleBogusSequenceNumber() throws Exception {
		// This covers a weird upstream regression/behavior change where the APNs gateway will send a sequence number of
		// zero if we send a notification with a zero-length token. See https://github.com/relayrides/pushy/issues/149
		// for additional discussion.

		this.getApnsServer().setShouldSendIncorrectSequenceNumber(true);

		try {
			final Object mutex = new Object();

			final TestListener listener = new TestListener(mutex);
			final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
					new ApnsConnection<SimpleApnsPushNotification>(
							TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(),
							new ApnsConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

			final CountDownLatch latch = this.getApnsServer().getAcceptedNotificationCountDownLatch(1);

			synchronized (mutex) {
				apnsConnection.connect();

				while (!listener.connectionSucceeded) {
					mutex.wait();
				}
			}

			assertTrue(listener.connectionSucceeded);

			apnsConnection.sendNotification(this.createTestNotification());
			this.waitForLatch(latch);

			synchronized (mutex) {
				apnsConnection.shutdownGracefully();

				while (!listener.connectionClosed) {
					mutex.wait();
				}
			}

			assertTrue(listener.connectionClosed);
			assertNull(listener.rejectedNotification);
			assertNull(listener.rejectionReason);
			assertTrue(listener.unprocessedNotifications.isEmpty());
		} finally {
			this.getApnsServer().setShouldSendIncorrectSequenceNumber(false);
		}
	}
}
