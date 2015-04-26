package com.relayrides.pushy.apns;

import io.netty.channel.EventLoopGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	private final Map<ApnsConnection<T>, ScheduledFuture<?>> connectionFutures = new HashMap<ApnsConnection<T>, ScheduledFuture<?>>();
	private final BlockingQueue<ApnsConnection<T>> writableConnections = new LinkedBlockingQueue<ApnsConnection<T>>();

	private long reconnectDelay = 0;

	private static final AtomicInteger GROUP_COUNTER = new AtomicInteger(0);
	private static final AtomicInteger CONNECTION_COUNTER = new AtomicInteger(0);

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
			for (int i = this.connections.size(); i < this.connectionCount; i++) {
				this.addConnectionWithDelay(0);
			}
		}
	}

	public void disconnectAllGracefuly() {
		this.shouldMaintainConnections = false;

		synchronized (this.connectionFutures) {
			for (final Map.Entry<ApnsConnection<T>, ScheduledFuture<?>> entry : this.connectionFutures.entrySet()) {
				if (entry.getValue().cancel(false)) {
					// If we successfully cancelled the connection future, we can just remove the connection immediately.
					// Otherwise, we'll have to wait for it to fail/close on its own.
					this.removeConnection(entry.getKey());
				}
			}

			this.connectionFutures.clear();
		}

		synchronized (this.connections) {
			for (final ApnsConnection<T> connection : this.connections) {
				connection.disconnectGracefully();
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
			final String connectionName = String.format("%s-%d", this.name, CONNECTION_COUNTER.getAndIncrement());

			final ApnsConnection<T> connection =
					new ApnsConnection<T>(this.environment, this.sslContext, this.eventLoopGroup, this.connectionConfiguration, this, connectionName);

			final ScheduledFuture<?> future = this.eventLoopGroup.schedule(new Runnable() {
				@Override
				public void run() {
					synchronized (ApnsConnectionGroup.this.connectionFutures) {
						connection.connect();
						ApnsConnectionGroup.this.connectionFutures.remove(connection);
					}
				}
			}, delayMillis, TimeUnit.MILLISECONDS);

			this.connectionFutures.put(connection, future);
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

	public boolean sendNotification(final T notification) throws InterruptedException {
		return this.sendNotification(notification, Long.MAX_VALUE);
	}

	public boolean sendNotification(final T notification, final long timeoutMillis) throws InterruptedException {
		final boolean notificationSent;
		final ApnsConnection<T> connection = this.writableConnections.poll(timeoutMillis, TimeUnit.MILLISECONDS);

		if (connection != null) {
			// TODO Figure out what to do if the connection's writabiliy changes after we get it, but before we return it
			// to the queue
			this.writableConnections.add(connection);

			connection.sendNotification(notification);

			notificationSent = true;
		} else {
			notificationSent = false;
		}

		return notificationSent;
	}

	@Override
	public void handleConnectionSuccess(final ApnsConnection<T> connection) {
		this.writableConnections.add(connection);
		this.reconnectDelay = 0;
	}

	@Override
	public void handleConnectionFailure(ApnsConnection<T> connection, Throwable cause) {
		if (this.shouldMaintainConnections) {
			addConnectionWithDelay(this.reconnectDelay);

			// This isn't really thread-safe, but the consequences of a screw-up are pretty mild, so we don't worry about
			// it too much.
			this.reconnectDelay = this.reconnectDelay == 0 ? INITIAL_RECONNECT_DELAY :
				Math.min(this.reconnectDelay * 2, MAX_RECONNECT_DELAY);
		}

		this.removeConnection(connection);
		this.listener.handleConnectionFailure(this, cause);
	}

	@Override
	public void handleConnectionWritabilityChange(ApnsConnection<T> connection, boolean writable) {
		if (writable) {
			this.writableConnections.add(connection);
		} else {
			this.writableConnections.remove(connection);
		}
	}

	@Override
	public void handleConnectionClosure(ApnsConnection<T> connection) {
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
