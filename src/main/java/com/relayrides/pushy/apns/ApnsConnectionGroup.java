package com.relayrides.pushy.apns;

import io.netty.channel.EventLoopGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

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

	private static final AtomicInteger GROUP_COUNTER = new AtomicInteger(0);

	private static final long INITIAL_RECONNECT_DELAY = 100;
	private static final long MAX_RECONNECT_DELAY = INITIAL_RECONNECT_DELAY * 512;

	public ApnsConnectionGroup(final ApnsEnvironment environment, final SSLContext sslContext, final EventLoopGroup eventLoopGroup, final ApnsConnectionConfiguration connectionConfiguration, final ApnsConnectionGroupListener<T> listener, final String name, final int connectionCount) {
		this.environment = environment;
		this.sslContext = sslContext;
		this.eventLoopGroup = eventLoopGroup;
		this.connectionConfiguration = connectionConfiguration;

		if (name != null) {
			this.name = name;
		} else {
			this.name = String.format("ApnsConnectionGroup-%d", GROUP_COUNTER.getAndIncrement());
		}

		this.listener = listener;
		this.connectionCount = connectionCount;
	}

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

	public void disconnectAllGracefuly() {
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

	public void waitForAllConnectionsToClose() throws InterruptedException {
		synchronized (this.connections) {
			while (!this.connections.isEmpty()) {
				this.connections.wait();
			}
		}
	}

	private void addConnectionWithDelay(final long delayMillis) {
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

	private void removeConnection(final ApnsConnection<T> connection) {
		synchronized (this.connections) {
			this.connections.remove(connection);

			if (this.connections.isEmpty()) {
				this.connections.notifyAll();
			}
		}
	}

	public ApnsConnection<T> getNextConnection() throws InterruptedException {
		return this.getNextConnection(Long.MAX_VALUE);
	}

	public ApnsConnection<T> getNextConnection(final long timeoutMillis) throws InterruptedException {
		final ApnsConnection<T> connection = this.writableConnections.poll(timeoutMillis, TimeUnit.MILLISECONDS);

		if (connection != null && connection.isWritable()) {
			this.writableConnections.add(connection);
		}

		return connection;
	}

	@Override
	public void handleConnectionSuccess(final ApnsConnection<T> connection) {
		this.writableConnections.add(connection);
		this.reconnectDelay = 0;
	}

	@Override
	public void handleConnectionFailure(final ApnsConnection<T> connection, final Throwable cause) {
		if (this.shouldMaintainConnections) {
			addConnectionWithDelay(this.reconnectDelay);

			// This isn't really thread-safe, but the consequences of a screw-up are pretty mild, so we don't worry
			// about it too much.
			this.reconnectDelay = this.reconnectDelay == 0 ? INITIAL_RECONNECT_DELAY :
				Math.min(this.reconnectDelay * 2, MAX_RECONNECT_DELAY);
		}

		this.removeConnection(connection);
		this.listener.handleConnectionFailure(this, cause);
	}

	@Override
	public void handleConnectionWritabilityChange(final ApnsConnection<T> connection, final boolean writable) {
		if (writable) {
			this.writableConnections.add(connection);
		} else {
			this.writableConnections.remove(connection);
		}
	}

	@Override
	public void handleConnectionClosure(final ApnsConnection<T> connection) {
		this.writableConnections.remove(connection);

		if (this.shouldMaintainConnections) {
			this.addConnectionWithDelay(0);
		}

		this.removeConnection(connection);
	}

	@Override
	public void handleWriteFailure(final ApnsConnection<T> connection, final T notification, final Throwable cause) {
		if (this.listener != null) {
			this.listener.handleWriteFailure(this, notification, cause);
		}
	}

	@Override
	public void handleRejectedNotification(final ApnsConnection<T> connection, final T rejectedNotification, final RejectedNotificationReason reason) {
		if (this.listener != null) {
			this.listener.handleRejectedNotification(this, rejectedNotification, reason);
		}
	}

	@Override
	public void handleUnprocessedNotifications(final ApnsConnection<T> connection, final Collection<T> unprocessedNotifications) {
		if (this.listener != null) {
			this.listener.handleUnprocessedNotifications(this, unprocessedNotifications);
		}
	}
}
