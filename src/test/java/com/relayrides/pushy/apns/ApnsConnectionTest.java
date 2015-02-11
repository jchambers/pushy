package com.relayrides.pushy.apns;

import static org.junit.Assert.assertTrue;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;

import org.junit.Test;

public abstract class ApnsConnectionTest extends BasePushyTest {

	public abstract ApnsConnection getTestConnection(ApnsEnvironment environment, SSLContext sslContext,
			TestConnectionListener listener);

	@Test
	public void testConnect() throws Exception {
		// For this test, we just want to make sure that connection succeeds and nothing explodes.
		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);
		final ApnsConnection apnsConnection = this.getTestConnection(TEST_ENVIRONMENT,
				SSLTestUtil.createSSLContextForTestClient(), listener);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.hasConnectionSucceeded()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionSucceeded());
	}

	@Test(expected = IllegalStateException.class)
	public void testDoubleConnect() throws Exception {

		final ApnsConnection apnsConnection = this.getTestConnection(TEST_ENVIRONMENT,
				SSLTestUtil.createSSLContextForTestClient(), new TestConnectionListener(new Object()));

		apnsConnection.connect();
		apnsConnection.connect();
	}

	@Test
	public void testConnectEmptyKeystore() throws Exception {

		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);
		final ApnsConnection apnsConnection = this.getTestConnection(TEST_ENVIRONMENT,
				SSLTestUtil.createSSLContextForTestClient("/empty-keystore.jks"), listener);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.hasConnectionFailed()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionFailed());
		assertTrue(listener.getConnectionFailureCause() instanceof SSLHandshakeException);
	}

	@Test
	public void testConnectUntrustedKeystore() throws Exception {

		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);
		final ApnsConnection apnsConnection = this.getTestConnection(TEST_ENVIRONMENT,
				SSLTestUtil.createSSLContextForTestClient("/pushy-test-client-untrusted.jks"), listener);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.hasConnectionFailed()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionFailed());
		assertTrue(listener.getConnectionFailureCause() instanceof SSLHandshakeException);
	}

	@Test
	public void testConnectionRefusal() throws Exception {
		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);
		final ApnsEnvironment connectionRefusedEnvironment = new ApnsEnvironment("localhost", 7876, "localhost", 7877);
		final ApnsConnection apnsConnection = this.getTestConnection(connectionRefusedEnvironment,
				SSLTestUtil.createSSLContextForTestClient(), listener);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.hasConnectionFailed()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionFailed());
	}

	@Test
	public void testShutdownImmediately() throws Exception {
		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);
		final ApnsConnection apnsConnection = this.getTestConnection(TEST_ENVIRONMENT,
				SSLTestUtil.createSSLContextForTestClient(), listener);

		synchronized (mutex) {
			apnsConnection.connect();

			while (!listener.hasConnectionSucceeded()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionSucceeded());

		synchronized (mutex) {
			apnsConnection.shutdownImmediately();

			while (!listener.hasConnectionClosed()) {
				mutex.wait();
			}
		}

		assertTrue(listener.hasConnectionClosed());
	}

	@Test
	public void testShutdownImmediatelyBeforeConnect() throws Exception {
		final Object mutex = new Object();

		final TestConnectionListener listener = new TestConnectionListener(mutex);
		final ApnsConnection apnsConnection = this.getTestConnection(TEST_ENVIRONMENT,
				SSLTestUtil.createSSLContextForTestClient(), listener);

		apnsConnection.shutdownImmediately();
	}

}
