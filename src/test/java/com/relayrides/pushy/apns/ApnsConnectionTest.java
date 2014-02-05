package com.relayrides.pushy.apns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import io.netty.channel.nio.NioEventLoopGroup;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class ApnsConnectionTest {

	private static final long LATCH_TIMEOUT_VALUE = 2;
	private static final TimeUnit LATCH_TIMEOUT_UNIT = TimeUnit.SECONDS;
	
	private static final ApnsEnvironment TEST_ENVIRONMENT =
			new ApnsEnvironment("localhost", 2195, "localhost", 2196);

	private NioEventLoopGroup workerGroup;

	private MockApnsServer apnsServer;

	private class TestListener implements ApnsConnectionListener<SimpleApnsPushNotification> {
		
		private final Object mutex;
		
		private boolean connectionSucceeded = false;
		private boolean connectionClosed = false;
		
		private final ArrayList<SimpleApnsPushNotification> writeFailures;
		
		private SimpleApnsPushNotification rejectedNotification;
		private RejectedNotificationReason rejectionReason;
		
		public TestListener(final Object mutex) {
			this.mutex = mutex;
			
			this.writeFailures = new ArrayList<SimpleApnsPushNotification>();
		}
		
		public void handleConnectionSuccess(final ApnsConnection<SimpleApnsPushNotification> connection) {
			synchronized (this.mutex) {
				this.connectionSucceeded = true;
				this.mutex.notifyAll();
			}
		}
		
		public void handleConnectionFailure(final ApnsConnection<SimpleApnsPushNotification> connection, final Throwable cause) {
			synchronized (mutex) {
				this.mutex.notifyAll();
			}
		}
		
		public void handleConnectionClosure(ApnsConnection<SimpleApnsPushNotification> connection) {
			synchronized (mutex) {
				this.connectionClosed = true;
				this.mutex.notifyAll();
			}
		}

		public void handleWriteFailure(ApnsConnection<SimpleApnsPushNotification> connection,
				SimpleApnsPushNotification notification, Throwable cause) {
			
			this.writeFailures.add(notification);
		}

		public void handleRejectedNotification(
				ApnsConnection<SimpleApnsPushNotification> connection,
				SimpleApnsPushNotification rejectedNotification,
				RejectedNotificationReason reason,
				Collection<SimpleApnsPushNotification> unprocessedNotifications) {
			
			this.rejectedNotification = rejectedNotification;
			this.rejectionReason = reason;
		}
	}
	
	@Before
	public void setUp() throws Exception {
		this.workerGroup = new NioEventLoopGroup();
		
		this.apnsServer = new MockApnsServer(TEST_ENVIRONMENT.getApnsGatewayPort());
		this.apnsServer.start();
	}

	@After
	public void tearDown() throws Exception {
		this.apnsServer.shutdown();
		this.workerGroup.shutdownGracefully().await();
	}

	@Test
	public void testConnect() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		// For this test, we just want to make sure that connection succeeds and nothing explodes.
		final Object mutex = new Object();
		
		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLUtil.createSSLContextForTestClient(), this.workerGroup, listener);
		
		synchronized (mutex) {
			apnsConnection.connect();
			mutex.wait(1000);
		}
		
		assertTrue(listener.connectionSucceeded);
	}
	
	@Test(expected = IllegalStateException.class)
	public void testDoubleConnect() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLUtil.createSSLContextForTestClient(), this.workerGroup,
						new TestListener(new Object()));
		
		apnsConnection.connect();
		apnsConnection.connect();
	}

	@Test
	public void testSendNotification() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final Object mutex = new Object();
		
		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLUtil.createSSLContextForTestClient(), this.workerGroup, listener);
		
		final CountDownLatch latch = this.apnsServer.getCountDownLatch(1);
		
		synchronized (mutex) {
			apnsConnection.connect();
			mutex.wait(1000);
		}
		
		assertTrue(listener.connectionSucceeded);
		
		apnsConnection.sendNotification(this.createTestNotification());
		this.waitForLatch(latch);
		
		assertEquals(1, this.apnsServer.getReceivedNotifications().size());
	}
	
	@Test
	public void testSendNotificationWithError() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final Object mutex = new Object();
		
		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLUtil.createSSLContextForTestClient(), this.workerGroup, listener);
		
		synchronized (mutex) {
			apnsConnection.connect();
			mutex.wait(1000);
		}
		
		assertTrue(listener.connectionSucceeded);
		
		final SimpleApnsPushNotification bogusNotification =
				new SimpleApnsPushNotification(new byte[] {}, "This is a bogus notification and should be rejected.");
		
		synchronized (mutex) {
			apnsConnection.sendNotification(bogusNotification);
			mutex.wait(1000);
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
						TEST_ENVIRONMENT, SSLUtil.createSSLContextForTestClient(), this.workerGroup, listener);
		
		synchronized (mutex) {
			apnsConnection.connect();
			mutex.wait(1000);
		}
		
		assertTrue(listener.connectionSucceeded);
		
		synchronized (mutex) {
			apnsConnection.shutdownGracefully();
			mutex.wait();
		}
		
		assertTrue(listener.connectionClosed);
	}

	@Test
	public void testShutdownImmediately() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final Object mutex = new Object();
		
		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLUtil.createSSLContextForTestClient(), this.workerGroup, listener);
		
		synchronized (mutex) {
			apnsConnection.connect();
			mutex.wait(1000);
		}
		
		assertTrue(listener.connectionSucceeded);
		
		synchronized (mutex) {
			apnsConnection.shutdownImmediately();;
			mutex.wait();
		}
		
		assertTrue(listener.connectionClosed);
	}
	
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
