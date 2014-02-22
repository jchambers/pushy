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

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>A group of connections to an APNs gateway. An `ApnsConnectionPool` rotates through the connections in the pool,
 * acting as a kind of load balancer. Additionally, the {@link ApnsConnectionPool#getNextConnection} method blocks
 * until connections are available before returning a result.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
class ApnsConnectionPool<T extends ApnsPushNotification> {

	private final ArrayList<ApnsConnection<T>> connections;

	private final Lock lock;
	private final Condition connectionAvailable;
	private final Condition poolEmpty;

	private int connectionIndex = 0;

	/**
	 * Constructs a new, empty connection pool.
	 */
	public ApnsConnectionPool() {
		this.connections = new ArrayList<ApnsConnection<T>>();

		this.lock = new ReentrantLock();
		this.connectionAvailable = this.lock.newCondition();
		this.poolEmpty = this.lock.newCondition();
	}

	/**
	 * Adds a connection to the pool.
	 *
	 * @param connection the connection to add to the pool
	 */
	public void addConnection(final ApnsConnection<T> connection) {
		this.lock.lock();

		try {
			this.connections.add(connection);
			this.connectionAvailable.signalAll();
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * Removes a connection from the pool.
	 *
	 * @param connection the connection to remove from the pool.
	 */
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

	/**
	 * Returns the next available connection from this pool, blocking until a connection is available or until the
	 * thread is interrupted. This method makes a reasonable effort to rotate through connections in the pool, and
	 * repeated calls will generally yield different connections when multiple connections are in the pool.
	 *
	 * @return the next available connection
	 *
	 * @throws InterruptedException if interrupted while waiting for a connection to become available
	 */
	public ApnsConnection<T> getNextConnection() throws InterruptedException {
		this.lock.lock();

		try {
			while (this.connections.isEmpty()) {
				this.connectionAvailable.await();
			}

			return this.connections.get(Math.abs(this.connectionIndex++ % this.connections.size()));
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * Returns all of the connections in this pool.
	 *
	 * @return a collection of all connections in this pool
	 */
	public Collection<ApnsConnection<T>> getAll() {
		this.lock.lock();

		try {
			return new ArrayList<ApnsConnection<T>>(this.connections);
		} finally {
			this.lock.unlock();
		}
	}
}
