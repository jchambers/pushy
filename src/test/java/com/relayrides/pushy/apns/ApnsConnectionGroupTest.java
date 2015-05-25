/* Copyright (c) 2015 RelayRides
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
import io.netty.util.concurrent.Future;

import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import org.junit.Before;
import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class ApnsConnectionGroupTest extends BasePushyTest {

	private ApnsConnectionGroup<SimpleApnsPushNotification> testGroup;

	private static final int CONNECTION_COUNT = 4;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		final DefaultApnsConnectionFactory<SimpleApnsPushNotification> connectionFactory =
				new DefaultApnsConnectionFactory<SimpleApnsPushNotification>(TEST_ENVIRONMENT,
						SSLTestUtil.createSSLContextForTestClient(), this.getEventLoopGroup(), "TestConnection");

		this.testGroup = new ApnsConnectionGroup<SimpleApnsPushNotification>(this.getEventLoopGroup(), connectionFactory,
				null, "TestGroup", CONNECTION_COUNT);
	}

	@Test
	public void testConnectAllAndDisconnectAllGracefully() throws Exception {
		final CountDownLatch connectionLatch =
				this.getApnsServer().getSuccessfulConnectionCountDownLatch(ApnsConnectionGroupTest.CONNECTION_COUNT);

		this.testGroup.connectAll();
		waitForLatch(connectionLatch);

		this.testGroup.disconnectAllGracefully();
		this.testGroup.waitForAllConnectionsToClose();
	}

	@Test
	public void testConnectAllAndDisconnectAllImmediately() throws Exception {
		final CountDownLatch connectionLatch =
				this.getApnsServer().getSuccessfulConnectionCountDownLatch(ApnsConnectionGroupTest.CONNECTION_COUNT);

		this.testGroup.connectAll();
		waitForLatch(connectionLatch);

		this.testGroup.disconnectAllImmediately();
		this.testGroup.waitForAllConnectionsToClose();
	}

	@Test
	public void testIncreaseConnectionDelay() throws Exception {
		assertEquals(0, testGroup.getConnectionDelay());

		this.testGroup.increaseConnectionDelay();
		assertEquals(ApnsConnectionGroup.INITIAL_RECONNECT_DELAY, this.testGroup.getConnectionDelay());

		while (this.testGroup.getConnectionDelay() < ApnsConnectionGroup.MAX_RECONNECT_DELAY) {
			this.testGroup.increaseConnectionDelay();
		}

		this.testGroup.increaseConnectionDelay();
		assertEquals(ApnsConnectionGroup.MAX_RECONNECT_DELAY, this.testGroup.getConnectionDelay());
	}

	@Test
	public void testResetConnectionDelay() throws Exception {
		assertEquals(0, this.testGroup.getConnectionDelay());

		this.testGroup.increaseConnectionDelay();
		assertTrue(this.testGroup.getConnectionDelay() > 0);

		this.testGroup.resetConnectionDelay();
		assertEquals(0, this.testGroup.getConnectionDelay());
	}

	@Test
	public void testWaitForAllConnectionsToCloseDate() throws Exception {
		final CountDownLatch connectionLatch =
				this.getApnsServer().getSuccessfulConnectionCountDownLatch(ApnsConnectionGroupTest.CONNECTION_COUNT);

		this.testGroup.connectAll();
		waitForLatch(connectionLatch);

		final Date deadline = new Date(System.currentTimeMillis() + 1000);

		assertFalse("Waiting for connections to close should timeout if closure has not been requested.",
				testGroup.waitForAllConnectionsToClose(deadline));

		this.testGroup.disconnectAllGracefully();
		this.testGroup.waitForAllConnectionsToClose(null);
	}

	@Test
	public void testGetNextConnection() throws Exception {
		final CountDownLatch connectionLatch = this.getApnsServer().getSuccessfulConnectionCountDownLatch(ApnsConnectionGroupTest.CONNECTION_COUNT);

		this.testGroup.connectAll();
		waitForLatch(connectionLatch);

		// This is a little hacky, but we want to make sure all of the listeners have fired before we start getting
		// connections. Otherwise, we might run into a race condition where we're getting connections before all of them
		// have been added to the writable connection pool.
		final Future<Void> getConnectionFuture = this.getEventLoopGroup().submit(new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				final ApnsConnection<SimpleApnsPushNotification> firstConnection =
						ApnsConnectionGroupTest.this.testGroup.getNextConnection();

				final ApnsConnection<SimpleApnsPushNotification> secondConnection =
						ApnsConnectionGroupTest.this.testGroup.getNextConnection();

				assertNotNull(firstConnection);
				assertNotNull(secondConnection);
				assertNotEquals(firstConnection, secondConnection);

				return null;
			}
		});

		getConnectionFuture.await();

		this.testGroup.disconnectAllGracefully();
		this.testGroup.waitForAllConnectionsToClose(null);
	}

	@Test
	public void testGetNextConnectionLong() throws Exception {
		final long timeoutMillis = 1000;
		assertNull(testGroup.getNextConnection(timeoutMillis));

		final CountDownLatch connectionLatch =
				this.getApnsServer().getSuccessfulConnectionCountDownLatch(ApnsConnectionGroupTest.CONNECTION_COUNT);

		this.testGroup.connectAll();
		waitForLatch(connectionLatch);

		assertNotNull(this.testGroup.getNextConnection(timeoutMillis));

		this.testGroup.disconnectAllGracefully();
		this.testGroup.waitForAllConnectionsToClose(null);
	}
}
