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

/**
 * <p>A group of connections to an APNs gateway. An {@code ApnsConnectionPool} rotates through the connections in the
 * pool, acting as a simple load balancer. Additionally, the {@link ApnsConnectionPool#getNextConnection} method blocks
 * until connections are available before returning a result.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
class ApnsConnectionPool<T extends ApnsPushNotification> {

	private final ArrayList<ApnsConnection<T>> connections = new ArrayList<ApnsConnection<T>>();

	private int connectionIndex = 0;

	/**
	 * Adds a connection to the pool.
	 *
	 * @param connection the connection to add to the pool
	 */
	public void addConnection(final ApnsConnection<T> connection) {
		synchronized (this.connections) {
			this.connections.add(connection);
			this.connections.notifyAll();
		}
	}

	/**
	 * Removes a connection from the pool.
	 *
	 * @param connection the connection to remove from the pool.
	 */
	public void removeConnection(final ApnsConnection<T> connection) {
		synchronized (this.connections) {
			this.connections.remove(connection);
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
		synchronized (this.connections) {
			while (this.connections.isEmpty()) {
				this.connections.wait();
			}

			return this.connections.get(Math.abs(this.connectionIndex++ % this.connections.size()));
		}
	}

	/**
	 * Returns all of the connections in this pool.
	 *
	 * @return a collection of all connections in this pool
	 */
	protected Collection<ApnsConnection<T>> getAll() {
		synchronized (this.connections) {
			return new ArrayList<ApnsConnection<T>>(this.connections);
		}
	}
}
