package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class ApnsConnectionPoolTest {

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

	@Test(timeout = 1000)
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

			new Thread(new Runnable() {

				public void run() {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
					}

					pool.addConnection(createTestConnection());
				}
			}).start();

			assertNotNull(pool.getNextConnection());
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

	@Test
	public void testWaitForEmptyPool() throws InterruptedException {
		final ApnsConnectionPool<SimpleApnsPushNotification> pool =
				new ApnsConnectionPool<SimpleApnsPushNotification>();

		final ApnsConnection<SimpleApnsPushNotification> testConnection = this.createTestConnection();

		pool.addConnection(testConnection);

		new Thread(new Runnable() {

			public void run() {
				try {
					Thread.sleep(50);
				} catch (InterruptedException ignored) {
				}

				pool.removeConnection(testConnection);
			}
		}).start();

		assertFalse(pool.getAll().isEmpty());
		pool.waitForEmptyPool(new Date(System.currentTimeMillis() + 1000));
		assertTrue(pool.getAll().isEmpty());
	}

	private ApnsConnection<SimpleApnsPushNotification> createTestConnection() {
		return new ApnsConnection<SimpleApnsPushNotification>(ApnsEnvironment.getSandboxEnvironment(), null, null, new ApnsConnectionListener<SimpleApnsPushNotification>() {
			public void handleConnectionSuccess(ApnsConnection<SimpleApnsPushNotification> connection) {}
			public void handleConnectionFailure(ApnsConnection<SimpleApnsPushNotification> connection, Throwable cause) {}
			public void handleConnectionClosure(ApnsConnection<SimpleApnsPushNotification> connection) {}
			public void handleWriteFailure(ApnsConnection<SimpleApnsPushNotification> connection, SimpleApnsPushNotification notification, Throwable cause) {}
			public void handleRejectedNotification(ApnsConnection<SimpleApnsPushNotification> connection, SimpleApnsPushNotification rejectedNotification, RejectedNotificationReason reason) {}
			public void handleUnprocessedNotifications(ApnsConnection<SimpleApnsPushNotification> connection, Collection<SimpleApnsPushNotification> unprocessedNotifications) {}
		});
	}
}
