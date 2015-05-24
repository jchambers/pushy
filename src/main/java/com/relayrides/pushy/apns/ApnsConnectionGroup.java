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

import io.netty.channel.EventLoopGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.relayrides.pushy.apns.util.DeadlineUtil;

/**
 * A connection group maintains a pool of connections to the APNs gateway. Callers can retrieve connections from the
 * group via the {@link ApnsConnectionGroup#getNextConnection()} method, and then use the returned connection to send
 * notifications.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public class ApnsConnectionGroup<T extends ApnsPushNotification> implements ApnsConnectionListener<T> {
	private final int connectionCount;

	private final ApnsEnvironment environment;
	private final SSLContext sslContext;
	private final EventLoopGroup eventLoopGroup;
	private final ApnsConnectionConfiguration connectionConfiguration;

	private final String name;
	private final ApnsConnectionGroupListener<T> listener;

	private volatile boolean shouldMaintainConnections = false;

	private final List<ApnsConnection<T>> connections = new ArrayList<ApnsConnection<T>>();
	private final List<ScheduledFuture<?>> connectionFutures = new ArrayList<ScheduledFuture<?>>();
	private final BlockingQueue<ApnsConnection<T>> writableConnections = new LinkedBlockingQueue<ApnsConnection<T>>();

	private final AtomicInteger connectionCounter = new AtomicInteger(0);

	private long reconnectDelay = 0;

	public static final long INITIAL_RECONNECT_DELAY = 100;
	public static final long MAX_RECONNECT_DELAY = INITIAL_RECONNECT_DELAY * 512;

	private static final Logger log = LoggerFactory.getLogger(ApnsConnectionGroup.class);

	/**
	 * Constructs a new connection group that will maintain connections to the APNs gateway in the given environment.
	 *
	 * @param environment the environment in which connections in this group will operate; must not be {@code null}
	 * @param sslContext an SSL context with the keys/certificates and trust managers connection in this group should
	 * use when communicating with the APNs gateway; must not be {@code null}
	 * @param eventLoopGroup the event loop group connections in this group should use for asynchronous network
	 * operations; must not be {@code null}
	 * @param connectionConfiguration the set of configuration options to use for connections in this group. The
	 * configuration object is copied and changes to the original object will not propagate to the connection after
	 * creation. Must not be {@code null}.
	 * @param listener the listener to which this group will report connection lifecycle events; may be {@code null}
	 * @param name a human-readable name for this group; must not be {@code null}
	 * @param connectionCount the number of concurrent connections to maintain to the APNs gateway
	 */
	public ApnsConnectionGroup(final ApnsEnvironment environment, final SSLContext sslContext,
			final EventLoopGroup eventLoopGroup, final ApnsConnectionConfiguration connectionConfiguration,
			final ApnsConnectionGroupListener<T> listener, final String name, final int connectionCount) {
		this.environment = environment;
		this.sslContext = sslContext;
		this.eventLoopGroup = eventLoopGroup;
		this.connectionConfiguration = connectionConfiguration;
		this.name = name;

		this.listener = listener;
		this.connectionCount = connectionCount;
	}

	/**
	 * Opens and begins maintaining connections to the APNs gateway. After this method has been called, connections in
	 * this group will be replaced when they close until the {@link ApnsConnectionGroup#disconnectAllGracefully()} or
	 * {@link ApnsConnectionGroup#disconnectAllImmediately()} methods are called.
	 */
	public void connectAll() {
		this.shouldMaintainConnections = true;

		synchronized (this.connections) {
			synchronized (this.connectionFutures) {
				for (int i = this.connections.size() + this.connectionFutures.size(); i < this.connectionCount; i++) {
					this.addConnectionWithDelay(0);
				}
			}
		}
	}

	/**
	 * Begins a graceful disconnection attempt for all connections in this group. After this method is called, this
	 * group will no longer restore connections when they close.
	 */
	public void disconnectAllGracefully() {
		this.shouldMaintainConnections = false;

		synchronized (this.connectionFutures) {
			for (final ScheduledFuture<?> future : this.connectionFutures) {
				future.cancel(false);
			}

			this.connectionFutures.clear();
		}

		synchronized (this.connections) {
			final ArrayList<ApnsConnection<T>> connectionsToRemove = new ArrayList<ApnsConnection<T>>();

			for (final ApnsConnection<T> connection : this.connections) {
				if (!connection.disconnectGracefully()) {
					// The connection couldn't be shut down gracefully either because it was already closed or had not
					// yet connected; either way, we can remove it immediately.
					connectionsToRemove.add(connection);
				}
			}

			for (final ApnsConnection<T> connection : connectionsToRemove) {
				this.removeConnection(connection);
			}
		}
	}

	/**
	 * Immediately disconnects all connections in this group. After this method is called, this group will no longer
	 * restore connections when they close.
	 */
	public void disconnectAllImmediately() {
		this.shouldMaintainConnections = false;

		synchronized (this.connectionFutures) {
			for (final ScheduledFuture<?> future : this.connectionFutures) {
				future.cancel(false);
			}

			this.connectionFutures.clear();
		}

		synchronized (this.connections) {
			for (final ApnsConnection<T> connection : this.connections) {
				connection.disconnectImmediately();
			}

			this.connections.clear();
		}
	}

	/**
	 * Wait until all connections in this group have closed completely.
	 *
	 * @throws InterruptedException if interrupted while waiting for all connections to close
	 */
	public void waitForAllConnectionsToClose() throws InterruptedException {
		this.waitForAllConnectionsToClose(null);
	}

	/**
	 * Exponentially increases the delay before opening the next new connection. This method will not increase the delay
	 * beyond {@value ApnsConnectionGroup#MAX_RECONNECT_DELAY} milliseconds.
	 */
	protected synchronized void increaseConnectionDelay() {
		this.reconnectDelay = this.reconnectDelay == 0 ? INITIAL_RECONNECT_DELAY :
			Math.min(this.reconnectDelay * 2, MAX_RECONNECT_DELAY);
	}

	/**
	 * Resets the delay before opening the next new connection to zero.
	 */
	protected synchronized void resetConnectionDelay() {
		this.reconnectDelay = 0;
	}

	/**
	 * Returns the delay that must pass before opening the next new connection.
	 *
	 * @return the delay that must pass before opening the next new connection
	 */
	protected synchronized long getConnectionDelay() {
		return this.reconnectDelay;
	}

	/**
	 * Wait until all connections in this group have closed completely or the given deadline has passed.
	 *
	 * @param deadline the time by which this method should return; if {@code null} this method will wait indefinitely
	 *
	 * @return {@code true} if all connections closed successfully or {@code false} if the given deadline elapsed before
	 * all connections closed
	 * @throws InterruptedException if interrupted while waiting for all connections to close
	 */
	public boolean waitForAllConnectionsToClose(final Date deadline) throws InterruptedException {
		synchronized (this.connections) {
			while (!this.connections.isEmpty() && !DeadlineUtil.hasDeadlineExpired(deadline)) {
				this.connections.wait(DeadlineUtil.getMillisToWaitForDeadline(deadline));
			}

			return this.connections.isEmpty();
		}
	}

	/**
	 * Schedules a connection to open after the given delay.
	 *
	 * @param delayMillis the delay, in milliseconds, after which the connection should open
	 */
	private void addConnectionWithDelay(final long delayMillis) {
		log.trace("{} will open a new connection after {} milliseconds.", this, delayMillis);

		synchronized (this.connectionFutures) {
			final ScheduledFuture<?> future = this.eventLoopGroup.schedule(new Runnable() {
				@Override
				public void run() {
					if (ApnsConnectionGroup.this.shouldMaintainConnections) {
						synchronized (ApnsConnectionGroup.this.connections) {
							final String connectionName = String.format("%s-%d", ApnsConnectionGroup.this.name, ApnsConnectionGroup.this.connectionCounter.getAndIncrement());

							final ApnsConnection<T> connection =
									new ApnsConnection<T>(ApnsConnectionGroup.this.environment, ApnsConnectionGroup.this.sslContext, ApnsConnectionGroup.this.eventLoopGroup, ApnsConnectionGroup.this.connectionConfiguration, ApnsConnectionGroup.this, connectionName);

							ApnsConnectionGroup.this.connections.add(connection);
							connection.connect();
						}
					}

					synchronized (ApnsConnectionGroup.this.connectionFutures) {
						ApnsConnectionGroup.this.connectionFutures.remove(this);
					}
				}
			}, delayMillis, TimeUnit.MILLISECONDS);

			this.connectionFutures.add(future);
		}
	}

	/**
	 * Removes a connection from this group and potentially alerts waiting threads that all connections have closed.
	 *
	 * @param connection the connection to remove from this group
	 */
	private void removeConnection(final ApnsConnection<T> connection) {
		log.trace("{} will remove connection {}.", this, connection);

		synchronized (this.connections) {
			this.connections.remove(connection);

			if (this.connections.isEmpty()) {
				this.connections.notifyAll();
			}
		}
	}

	/**
	 * Gets the next writable connection from this group, blocking indefinitely until a connection is available.
	 *
	 * @return the next writable connection from this group
	 * @throws InterruptedException if interrupted while waiting for a connection to become available
	 */
	public ApnsConnection<T> getNextConnection() throws InterruptedException {
		return this.getNextConnection(Long.MAX_VALUE);
	}

	/**
	 * Gets the next writable connection from this group, blocking for up to the given period until a connection is
	 * available.
	 *
	 * @param timeoutMillis the maximum time to wait for a connection
	 *
	 * @return the next writable connection from this group, or {@code null} if no connection is available before the
	 * given timeout elapses
	 * @throws InterruptedException if interrupted while waiting for a connection to become available
	 */
	public ApnsConnection<T> getNextConnection(final long timeoutMillis) throws InterruptedException {
		final ApnsConnection<T> connection = this.writableConnections.poll(timeoutMillis, TimeUnit.MILLISECONDS);

		if (connection != null && connection.isWritable()) {
			this.writableConnections.add(connection);
		}

		return connection;
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleConnectionSuccess(com.relayrides.pushy.apns.ApnsConnection)
	 */
	@Override
	public void handleConnectionSuccess(final ApnsConnection<T> connection) {
		log.trace("{} successfully opened connection {}.", this, connection);

		this.writableConnections.add(connection);
		this.resetConnectionDelay();
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleConnectionFailure(com.relayrides.pushy.apns.ApnsConnection, java.lang.Throwable)
	 */
	@Override
	public void handleConnectionFailure(final ApnsConnection<T> connection, final Throwable cause) {
		log.trace("{} failed to open connection {}.", this, connection);

		if (this.shouldMaintainConnections) {
			addConnectionWithDelay(this.getConnectionDelay());
			this.increaseConnectionDelay();
		}

		this.removeConnection(connection);

		if (this.listener != null) {
			this.listener.handleConnectionFailure(this, cause);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleConnectionWritabilityChange(com.relayrides.pushy.apns.ApnsConnection, boolean)
	 */
	@Override
	public void handleConnectionWritabilityChange(final ApnsConnection<T> connection, final boolean writable) {
		log.trace("Writability for {} changed to {}", connection, writable);

		if (writable) {
			this.writableConnections.add(connection);
		} else {
			this.writableConnections.remove(connection);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleConnectionClosure(com.relayrides.pushy.apns.ApnsConnection)
	 */
	@Override
	public void handleConnectionClosure(final ApnsConnection<T> connection) {
		log.trace("Connection closed: {}", connection);

		this.writableConnections.remove(connection);

		if (this.shouldMaintainConnections) {
			this.addConnectionWithDelay(this.getConnectionDelay());
		}

		this.removeConnection(connection);
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleWriteFailure(com.relayrides.pushy.apns.ApnsConnection, com.relayrides.pushy.apns.ApnsPushNotification, java.lang.Throwable)
	 */
	@Override
	public void handleWriteFailure(final ApnsConnection<T> connection, final T notification, final Throwable cause) {
		if (this.listener != null) {
			this.listener.handleWriteFailure(this, notification, cause);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleRejectedNotification(com.relayrides.pushy.apns.ApnsConnection, com.relayrides.pushy.apns.ApnsPushNotification, com.relayrides.pushy.apns.RejectedNotificationReason)
	 */
	@Override
	public void handleRejectedNotification(final ApnsConnection<T> connection, final T rejectedNotification, final RejectedNotificationReason reason) {
		log.trace("{} rejected {}: {}", connection, rejectedNotification, reason);

		if (this.listener != null) {
			this.listener.handleRejectedNotification(this, rejectedNotification, reason);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleUnprocessedNotifications(com.relayrides.pushy.apns.ApnsConnection, java.util.Collection)
	 */
	@Override
	public void handleUnprocessedNotifications(final ApnsConnection<T> connection, final Collection<T> unprocessedNotifications) {
		log.trace("{} returned {} unprocessed notifications", connection, unprocessedNotifications.size());

		if (this.listener != null) {
			this.listener.handleUnprocessedNotifications(this, unprocessedNotifications);
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("ApnsConnectionGroup [name=");
		builder.append(name);
		builder.append("]");
		return builder.toString();
	}
}
