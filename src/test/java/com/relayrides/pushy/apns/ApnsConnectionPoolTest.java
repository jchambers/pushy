package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class ApnsConnectionPoolTest {

	private ApnsConnectionPool<SimpleApnsPushNotification> pool;

	@Before
	public void setUp() throws Exception {
		this.pool = new ApnsConnectionPool<SimpleApnsPushNotification>();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testAddConnection() {
		assertEquals(0, this.pool.getAll().size());
		this.pool.addConnection(this.createTestConnection());
		assertEquals(1, this.pool.getAll().size());
	}

	@Test
	public void testRemoveConnection() {
		assertEquals(0, this.pool.getAll().size());

		final ApnsConnection<SimpleApnsPushNotification> testConnection = this.createTestConnection();
		this.pool.addConnection(testConnection);

		assertEquals(1, this.pool.getAll().size());

		this.pool.removeConnection(testConnection);
		assertEquals(0, this.pool.getAll().size());
	}

	@Test(timeout = 1000)
	public void testGetNextConnection() throws InterruptedException {

		new Thread(new Runnable() {

			public void run() {
				try {
					// TODO Make this less hacky
					Thread.sleep(50);
				} catch (InterruptedException e) {
				}

				pool.addConnection(createTestConnection());
			}
		}).start();

		assertNotNull(this.pool.getNextConnection());
	}

	@Test
	public void testGetAll() {
		final ApnsConnection<SimpleApnsPushNotification> firstConnection = this.createTestConnection();
		final ApnsConnection<SimpleApnsPushNotification> secondConnection = this.createTestConnection();

		this.pool.addConnection(firstConnection);
		this.pool.addConnection(secondConnection);

		assertEquals(2, this.pool.getAll().size());
		assertTrue(this.pool.getAll().contains(firstConnection));
		assertTrue(this.pool.getAll().contains(secondConnection));
	}

	@Test
	public void testWaitForEmptyPool() throws InterruptedException {
		final ApnsConnection<SimpleApnsPushNotification> testConnection = this.createTestConnection();

		this.pool.addConnection(testConnection);

		new Thread(new Runnable() {

			public void run() {
				try {
					// TODO This is a little hacky
					Thread.sleep(50);
				} catch (InterruptedException ignored) {
				}

				pool.removeConnection(testConnection);
			}
		}).start();

		assertFalse(this.pool.getAll().isEmpty());
		this.pool.waitForEmptyPool(new Date(System.currentTimeMillis() + 1000));
		assertTrue(this.pool.getAll().isEmpty());
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
