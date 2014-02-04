package com.relayrides.pushy.apns;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ApnsConnectionPool<T extends ApnsPushNotification> {

	private final ArrayList<ApnsConnection<T>> connections;

	private final Lock lock;
	private final Condition connectionAvailable;
	private final Condition poolEmpty;

	private int connectionIndex = 0;

	public ApnsConnectionPool() {
		this.connections = new ArrayList<ApnsConnection<T>>();

		this.lock = new ReentrantLock();
		this.connectionAvailable = this.lock.newCondition();
		this.poolEmpty = this.lock.newCondition();
	}

	public void addConnection(final ApnsConnection<T> connection) {
		this.lock.lock();

		try {
			this.connections.add(connection);
			this.connectionAvailable.signalAll();
		} finally {
			this.lock.unlock();
		}
	}

	public void removeConnection(final ApnsConnection<T> connection) {
		this.lock.lock();

		try {
			this.connections.remove(connection);

			if (this.connections.isEmpty()) {
				this.poolEmpty.signalAll();
			}
		} finally {
			this.lock.unlock();
		}
	}

	public ApnsConnection<T> getNextConnection() throws InterruptedException {
		this.lock.lock();

		try {
			while (this.connections.isEmpty()) {
				this.connectionAvailable.await();
			}

			return this.connections.get(this.connectionIndex++ % this.connections.size());
		} finally {
			this.lock.unlock();
		}
	}

	public List<ApnsConnection<T>> getAll() {
		this.lock.lock();

		try {
			return new ArrayList<ApnsConnection<T>>(this.connections);
		} finally {
			this.lock.unlock();
		}
	}

	public void waitForEmptyPool(final Date deadline) throws InterruptedException {
		this.lock.lock();

		try {
			while (!this.connections.isEmpty()) {
				if (deadline != null) {
					this.poolEmpty.awaitUntil(deadline);
				} else {
					this.poolEmpty.await();
				}
			}
		} finally {
			this.lock.unlock();
		}
	}
}
