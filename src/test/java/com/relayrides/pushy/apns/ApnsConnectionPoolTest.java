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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import io.netty.channel.nio.NioEventLoopGroup;

import javax.net.ssl.SSLContext;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class ApnsConnectionPoolTest {

	private static NioEventLoopGroup eventLoopGroup;

	@BeforeClass
	public static void setUpBeforeClass() {
		ApnsConnectionPoolTest.eventLoopGroup = new NioEventLoopGroup();
	}

	@AfterClass
	public static void tearDownAfterClass() throws InterruptedException {
		ApnsConnectionPoolTest.eventLoopGroup.shutdownGracefully().await();
	}

	@Test
	public void testAddConnection() {
		final ApnsConnectionPool<SimpleApnsPushNotification> pool =
				new ApnsConnectionPool<SimpleApnsPushNotification>();

		assertEquals(0, pool.getAll().size());
		pool.addConnection(this.createTestConnection());
		assertEquals(1, pool.getAll().size());
	}

	@Test
	public void testRemoveConnection() {
		final ApnsConnectionPool<SimpleApnsPushNotification> pool =
				new ApnsConnectionPool<SimpleApnsPushNotification>();

		assertEquals(0, pool.getAll().size());

		final ApnsConnection<SimpleApnsPushNotification> testConnection = this.createTestConnection();
		pool.addConnection(testConnection);

		assertEquals(1, pool.getAll().size());

		pool.removeConnection(testConnection);
		assertEquals(0, pool.getAll().size());
	}

	@Test
	public void testGetNextConnection() throws InterruptedException {

		{
			final ApnsConnectionPool<SimpleApnsPushNotification> pool =
					new ApnsConnectionPool<SimpleApnsPushNotification>();

			final ApnsConnection<SimpleApnsPushNotification> firstConnection = this.createTestConnection();

			pool.addConnection(firstConnection);

			assertEquals(firstConnection, pool.getNextConnection());

			final ApnsConnection<SimpleApnsPushNotification> secondConnection = this.createTestConnection();
			pool.addConnection(secondConnection);

			assertNotEquals(firstConnection, secondConnection);
			assertNotEquals("Subsequent calls to getNextConnection should return different connections.",
					pool.getNextConnection(), pool.getNextConnection());
		}

		{
			final ApnsConnectionPool<SimpleApnsPushNotification> pool =
					new ApnsConnectionPool<SimpleApnsPushNotification>();

			final Object mutex = new Object();

			final class ConnectionConsumerThread extends Thread {

				private ApnsConnection<SimpleApnsPushNotification> connection;

				@Override
				public void run() {
					try {
						this.connection = pool.getNextConnection();

						synchronized (mutex) {
							mutex.notifyAll();
						}
					} catch (InterruptedException e) {
					}
				}
			}

			final ConnectionConsumerThread consumerThread = new ConnectionConsumerThread();

			consumerThread.start();

			while (!Thread.State.WAITING.equals(consumerThread.getState())) {
				Thread.yield();
			}

			// At this point, we know the consumer thread is waiting for a connection
			synchronized (mutex) {
				pool.addConnection(this.createTestConnection());
				mutex.wait();
			}

			assertNotNull(consumerThread.connection);
		}
	}

	@Test
	public void testGetAll() {
		final ApnsConnectionPool<SimpleApnsPushNotification> pool =
				new ApnsConnectionPool<SimpleApnsPushNotification>();

		final ApnsConnection<SimpleApnsPushNotification> firstConnection = this.createTestConnection();
		final ApnsConnection<SimpleApnsPushNotification> secondConnection = this.createTestConnection();

		pool.addConnection(firstConnection);
		pool.addConnection(secondConnection);

		assertEquals(2, pool.getAll().size());
		assertTrue(pool.getAll().contains(firstConnection));
		assertTrue(pool.getAll().contains(secondConnection));
	}

	private ApnsConnection<SimpleApnsPushNotification> createTestConnection() {
		try {
			return new ApnsConnection<SimpleApnsPushNotification>(ApnsEnvironment.getSandboxEnvironment(),
					SSLContext.getDefault(), ApnsConnectionPoolTest.eventLoopGroup, new ApnsConnectionConfiguration(),
					null, "Test connection");
		} catch (Exception e) {
			throw new RuntimeException("Failed to create test connection.", e);
		}
	}
}
