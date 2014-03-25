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

import io.netty.channel.nio.NioEventLoopGroup;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>A {@code PushManager} is the main public-facing point of interaction with APNs. Push managers manage the queue of
 * outbound push notifications and manage connections to the various APNs servers. Push managers should always be
 * created using the {@link PushManagerFactory} class.</p>
 *
 * <p>Callers send push notifications by adding them to the push manager's queue. The push manager will send
 * notifications from the queue as quickly as it is able to do so, and will never put notifications back in the queue
 * (push managers maintain a separate, internal queue for notifications that should be re-sent).</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 * @see PushManagerFactory
 */
public class PushManager<T extends ApnsPushNotification> implements ApnsConnectionListener<T> {
	private final BlockingQueue<T> queue;
	private final LinkedBlockingQueue<T> retryQueue;

	private final ApnsEnvironment environment;
	private final SSLContext sslContext;
	private final int concurrentConnectionCount;
	private final HashSet<ApnsConnection<T>> activeConnections;
	private final ApnsConnectionPool<T> writableConnectionPool;
	private final FeedbackServiceClient feedbackServiceClient;

	private final Vector<RejectedNotificationListener<? super T>> rejectedNotificationListeners;
	private final Vector<FailedConnectionListener<? super T>> failedConnectionListeners;

	private Thread dispatchThread;
	private final NioEventLoopGroup eventLoopGroup;
	private final boolean shouldShutDownEventLoopGroup;

	private final ExecutorService listenerExecutorService;
	private final boolean shouldShutDownListenerExecutorService;

	private volatile boolean drainingRetryQueue = false;
	private volatile boolean drainingFinished = false;

	private final Logger log = LoggerFactory.getLogger(PushManager.class);

	private static class DispatchThreadExceptionHandler<T extends ApnsPushNotification> implements UncaughtExceptionHandler {
		private final Logger log = LoggerFactory.getLogger(DispatchThreadExceptionHandler.class);

		final PushManager<T> manager;

		public DispatchThreadExceptionHandler(final PushManager<T> manager) {
			this.manager = manager;
		}

		public void uncaughtException(final Thread t, final Throwable e) {
			log.error("Dispatch thread died unexpectedly. Please file a bug with the exception details.", e);

			if (!this.manager.drainingFinished) {
				this.manager.createAndStartDispatchThread();
			}
		}
	}

	/**
	 * <p>Constructs a new {@code PushManager} that operates in the given environment with the given SSL context and the
	 * given number of parallel connections to APNs. See
	 * <a href="http://developer.apple.com/library/mac/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/CommunicatingWIthAPS.html#//apple_ref/doc/uid/TP40008194-CH101-SW6">
	 * Best Practices for Managing Connections</a> for additional information.</p>
	 *
	 * <p>This constructor may take an event loop group as an argument; if an event loop group is provided, the caller
	 * is responsible for managing the lifecycle of the group and <strong>must</strong> shut it down after shutting down
	 * this {@code PushManager}.</p>
	 *
	 * @param environment the environment in which this {@code PushManager} operates
	 * @param sslContext the SSL context in which APNs connections controlled by this {@code PushManager} will operate
	 * @param concurrentConnectionCount the number of parallel connections to maintain
	 * @param eventLoopGroup the event loop group this push manager should use for its connections to the APNs gateway and
	 * feedback service; if {@code null}, a new event loop group will be created and will be shut down automatically
	 * when the push manager is shut down. If not {@code null}, the caller <strong>must</strong> shut down the event
	 * loop group after shutting down the push manager.
	 * @param listenerExecutorService the executor service this push manager should use to dispatch notifications to
	 * registered listeners. If {@code null}, a new single-thread executor service will be created and will be shut
	 * down automatically with the push manager is shut down. If not {@code null}, the caller <strong>must</strong>
	 * shut down the executor service after shutting down the push manager.
	 * @param queue the queue to be used to pass new notifications to this push manager
	 */
	protected PushManager(final ApnsEnvironment environment, final SSLContext sslContext,
			final int concurrentConnectionCount, final NioEventLoopGroup eventLoopGroup,
			final ExecutorService listenerExecutorService, final BlockingQueue<T> queue) {

		this.queue = queue != null ? queue : new LinkedBlockingQueue<T>();
		this.retryQueue = new LinkedBlockingQueue<T>();

		this.rejectedNotificationListeners = new Vector<RejectedNotificationListener<? super T>>();
		this.failedConnectionListeners = new Vector<FailedConnectionListener<? super T>>();

		this.environment = environment;
		this.sslContext = sslContext;

		this.concurrentConnectionCount = concurrentConnectionCount;
		this.writableConnectionPool = new ApnsConnectionPool<T>();
		this.activeConnections = new HashSet<ApnsConnection<T>>();

		if (eventLoopGroup != null) {
			this.eventLoopGroup = eventLoopGroup;
			this.shouldShutDownEventLoopGroup = false;
		} else {
			this.eventLoopGroup = new NioEventLoopGroup();
			this.shouldShutDownEventLoopGroup = true;
		}

		if (listenerExecutorService != null) {
			this.listenerExecutorService = listenerExecutorService;
			this.shouldShutDownListenerExecutorService = false;
		} else {
			this.listenerExecutorService = Executors.newSingleThreadExecutor();
			this.shouldShutDownListenerExecutorService = true;
		}

		this.feedbackServiceClient = new FeedbackServiceClient(this.environment, this.sslContext, this.eventLoopGroup);
	}

	/**
	 * <p>Opens all connections to APNs and prepares to send push notifications. Note that enqueued push notifications
	 * will <strong>not</strong> be sent until this method is called.</p>
	 *
	 * <p>Push managers may only be started once and cannot be reused after being shut down.</p>
	 *
	 * @throws IllegalStateException if the push manager has already been started or has already been shut down
	 */
	public synchronized void start() {
		if (this.isStarted()) {
			throw new IllegalStateException("Push manager has already been started.");
		}

		if (this.isShutDown()) {
			throw new IllegalStateException("Push manager has already been shut down and may not be restarted.");
		}

		for (int i = 0; i < this.concurrentConnectionCount; i++) {
			this.startNewConnection();
		}

		this.createAndStartDispatchThread();
	}

	private void createAndStartDispatchThread() {
		this.dispatchThread = createDispatchThread();
		this.dispatchThread.setUncaughtExceptionHandler(new DispatchThreadExceptionHandler<T>(this));
		this.dispatchThread.start();
	}

	protected Thread createDispatchThread() {
		return new Thread(new Runnable() {
			public void run() {
				while (!drainingFinished) {
					try {
						final ApnsConnection<T> connection = writableConnectionPool.getNextConnection();

						final T notificationToRetry = retryQueue.poll();

						if (notificationToRetry == null) {
							if (drainingRetryQueue) {
								// We're trying to drain the retry queue, which is now empty. Attempt to close all
								// open connections gracefully and see if the retry queue stays empty.
								for (final ApnsConnection<T> connectionToClose : activeConnections) {
									connectionToClose.shutdownGracefully();
								}

								synchronized (this) {
									// Park here until interrupted (we'll never be notified)
									this.wait();
								}
							} else {
								// We'll park here either until a new notification is available from the outside or until
								// something shows up in the retry queue, at which point we'll be interrupted.
								connection.sendNotification(queue.take());
							}
						} else {
							connection.sendNotification(notificationToRetry);
						}

					} catch (InterruptedException e) {
						continue;
					}
				}
			}

		});
	}

	/**
	 * Indicates whether this push manager has been started and not yet shut down.
	 *
	 * @return {@code true} if this push manager has been started and has not yet been shut down or {@code false}
	 * otherwise
	 */
	public boolean isStarted() {
		if (this.isShutDown()) {
			return false;
		} else {
			return this.dispatchThread != null;
		}
	}

	/**
	 * Indicates whether this push manager has been shut down (or is in the process of shutting down). Once a push
	 * manager has been shut down, it may not be restarted.
	 *
	 * @return {@code true} if this push manager has been shut down or is in the process of shutting down or
	 * {@code false} otherwise
	 */
	public boolean isShutDown() {
		return this.drainingRetryQueue || this.drainingFinished;
	}

	/**
	 * <p>Disconnects from APNs and gracefully shuts down all connections. This method will block until the internal
	 * retry queue has been emptied and until all connections have shut down gracefully. Calling this method is
	 * identical to calling {@link PushManager#shutdown(long)} with a timeout of {@code 0}.</p>
	 * 
	 * <p>By the time this method return normally, all notifications removed from the public queue are guaranteed to
	 * have been delivered to the APNs gateway and either accepted or rejected (i.e. the state of all sent
	 * notifications is known).</p>
	 *
	 * @throws InterruptedException if interrupted while waiting for connections to close cleanly
	 * @throws IllegalStateException if this method is called before the push manager has been started
	 */
	public synchronized void shutdown() throws InterruptedException {
		this.shutdown(0);
	}

	/**
	 * <p>Disconnects from the APNs and gracefully shuts down all connections. This method will wait until the given
	 * timeout expires for the internal retry queue to empty and for connections to close gracefully, and will then
	 * instruct them to shut down as soon as possible (and will block until shutdown is complete).</p>
	 * 
	 * <p>This method returns a collection of notifications that have not been sent by the time this push manager has
	 * shut down. If this method is called with a non-zero timeout, the list will contain all of the notifications in
	 * the push manager's internal retry queue. It will <em>not</em> contain notifications in the public queue (since
	 * the public queue can be checked directly). When shutting down with a non-zero timeout, no guarantees are made
	 * that notifications that were sent (i.e. are in neither the public queue nor the retry queue) were actually
	 * received or processed by the APNs gateway.</p>
	 * 
	 * <p>If called with a timeout of {@code 0}, the returned collection of unsent notifications will be empty. By the
	 * time this method exits, all notifications taken from the public queue are guaranteed to have been delivered to
	 * the APNs gateway and either accepted or rejected (i.e. the state of all sent notifications is known).</p>
	 *
	 * @param timeout the timeout, in milliseconds, after which client threads should be shut down as quickly as
	 * possible; if {@code 0}, this method will wait indefinitely
	 *
	 * @return a list of notifications not sent before the {@code PushManager} shut down
	 *
	 * @throws InterruptedException if interrupted while waiting for connections to close cleanly
	 * @throws IllegalStateException if this method is called before the push manager has been started
	 */
	public synchronized Collection<T> shutdown(long timeout) throws InterruptedException {
		if (this.isShutDown()) {
			log.warn("Push manager has already been shut down; shutting down multiple times is harmless, but may "
					+ "indicate a problem elsewhere.");
		}

		if (this.drainingFinished) {
			// We COULD throw an IllegalStateException here, but it seems unnecessary when we could just silently return
			// the same result without harm.
			return new ArrayList<T>(this.retryQueue);
		}

		if (!this.isStarted()) {
			throw new IllegalStateException("Push manager has not yet been started and cannot be shut down.");
		}

		this.drainingRetryQueue = true;

		if (this.dispatchThread != null) {
			this.dispatchThread.interrupt();
		}

		this.waitForAllOperationsToFinish(timeout > 0 ? new Date(System.currentTimeMillis() + timeout) : null);

		this.drainingFinished = true;

		if (this.dispatchThread != null) {
			this.dispatchThread.interrupt();
			this.dispatchThread.join();
		}

		this.rejectedNotificationListeners.clear();
		this.failedConnectionListeners.clear();

		if (this.shouldShutDownListenerExecutorService) {
			this.listenerExecutorService.shutdown();
		}

		if (this.shouldShutDownEventLoopGroup) {
			if (!this.eventLoopGroup.isShutdown()) {
				this.eventLoopGroup.shutdownGracefully().await();
			}
		}

		return new ArrayList<T>(this.retryQueue);
	}

	/**
	 * <p>Registers a listener for notifications rejected by APNs for specific reasons.</p>
	 *
	 * @param listener the listener to register
	 *
	 * @throws IllegalStateException if this push manager has already been shut down
	 *
	 * @see PushManager#unregisterRejectedNotificationListener(RejectedNotificationListener)
	 */
	public void registerRejectedNotificationListener(final RejectedNotificationListener<? super T> listener) {
		if (this.isShutDown()) {
			throw new IllegalStateException("Rejected notification listeners may not be registered after a push manager has been shut down.");
		}

		this.rejectedNotificationListeners.add(listener);
	}

	/**
	 * <p>Un-registers a rejected notification listener.</p>
	 *
	 * @param listener the listener to un-register
	 *
	 * @return {@code true} if the given listener was registered with this push manager and removed or {@code false} if
	 * the listener was not already registered with this push manager
	 */
	public boolean unregisterRejectedNotificationListener(final RejectedNotificationListener<? super T> listener) {
		return this.rejectedNotificationListeners.remove(listener);
	}

	/**
	 * <p>Registers a listener for failed attempts to connect to the APNs gateway.</p>
	 * 
	 * @param listener the listener to register
	 * 
	 * @throws IllegalStateException if this push manager has already been shut down
	 * 
	 * @see PushManager#unregisterFailedConnectionListener(FailedConnectionListener)
	 */
	public void registerFailedConnectionListener(final FailedConnectionListener<? super T> listener) {
		if (this.isShutDown()) {
			throw new IllegalStateException("Failed connection listeners may not be registered after a push manager has been shut down.");
		}

		this.failedConnectionListeners.add(listener);
	}

	/**
	 * <p>Un-registers a connection failure listener.</p>
	 * 
	 * @param listener the listener to un-register
	 * 
	 * @return {@code true} if the given listener was registered with this push manager and removed or {@code false} if
	 * the listener was not already registered with this push manager
	 */
	public boolean unregisterFailedConnectionListener(final FailedConnectionListener<? super T> listener) {
		return this.failedConnectionListeners.remove(listener);
	}

	/**
	 * <p>Returns the queue of messages to be sent to the APNs gateway. Callers should add notifications to this queue
	 * directly to send notifications. Notifications will be removed from this queue by Pushy when a send attempt is
	 * started, but no guarantees are made as to when the notification will actually be sent. Successful delivery is
	 * not acknowledged by the APNs gateway. Notifications rejected by APNs for specific reasons will be passed to
	 * registered {@link RejectedNotificationListener}s, and notifications that could not be sent due to temporary I/O
	 * problems will be scheduled for re-transmission in a separate, internal queue.</p>
	 *
	 * <p>Notifications in this queue will only be consumed when the {@code PushManager} is running, has active
	 * connections, and the internal &quot;retry queue&quot; is empty.</p>
	 *
	 * @return the queue of new notifications to send to the APNs gateway
	 *
	 * @see PushManager#registerRejectedNotificationListener(RejectedNotificationListener)
	 */
	public BlockingQueue<T> getQueue() {
		return this.queue;
	}

	protected BlockingQueue<T> getRetryQueue() {
		return this.retryQueue;
	}

	/**
	 * <p>Queries the APNs feedback service for expired tokens using a reasonable default timeout. Be warned that this
	 * is a <strong>destructive operation</strong>. According to Apple's documentation:</p>
	 *
	 * <blockquote>The feedback service’s list is cleared after you read it. Each time you connect to the feedback
	 * service, the information it returns lists only the failures that have happened since you last
	 * connected.</blockquote>
	 *
	 * <p>The push manager must be started before calling this method.</p>
	 *
	 * @return a list of tokens that have expired since the last connection to the feedback service
	 *
	 * @throws InterruptedException if interrupted while waiting for a response from the feedback service
	 * @throws FeedbackConnectionException if the attempt to connect to the feedback service failed for any reason
	 */
	public List<ExpiredToken> getExpiredTokens() throws InterruptedException, FeedbackConnectionException {
		return this.getExpiredTokens(1, TimeUnit.SECONDS);
	}

	/**
	 * <p>Queries the APNs feedback service for expired tokens using the given timeout. Be warned that this is a
	 * <strong>destructive operation</strong>. According to Apple's documentation:</p>
	 *
	 * <blockquote>The feedback service's list is cleared after you read it. Each time you connect to the feedback
	 * service, the information it returns lists only the failures that have happened since you last
	 * connected.</blockquote>
	 *
	 * <p>The push manager must be started before calling this method.</p>
	 *
	 * @param timeout the time after the last received data after which the connection to the feedback service should
	 * be closed
	 * @param timeoutUnit the unit of time in which the given {@code timeout} is measured
	 *
	 * @return a list of tokens that have expired since the last connection to the feedback service
	 *
	 * @throws InterruptedException if interrupted while waiting for a response from the feedback service
	 * @throws FeedbackConnectionException if the attempt to connect to the feedback service failed for any reason
	 * @throws IllegalStateException if this push manager has not been started yet or has already been shut down
	 */
	public List<ExpiredToken> getExpiredTokens(final long timeout, final TimeUnit timeoutUnit) throws InterruptedException, FeedbackConnectionException {
		if (!this.isStarted()) {
			throw new IllegalStateException("Push manager has not been started yet.");
		}

		if (this.isShutDown()) {
			throw new IllegalStateException("Push manager has already been shut down.");
		}

		return this.feedbackServiceClient.getExpiredTokens(timeout, timeoutUnit);
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleConnectionSuccess(com.relayrides.pushy.apns.ApnsConnection)
	 */
	public void handleConnectionSuccess(final ApnsConnection<T> connection) {
		if (this.drainingFinished) {
			// We DON'T want to decrement the counter here; we'll do so when handleConnectionClosure fires later
			connection.shutdownImmediately();
		} else {
			this.writableConnectionPool.addConnection(connection);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleConnectionFailure(com.relayrides.pushy.apns.ApnsConnection, java.lang.Throwable)
	 */
	public void handleConnectionFailure(final ApnsConnection<T> connection, final Throwable cause) {

		this.removeActiveConnection(connection);

		// We tried to open a connection, but failed. As long as we're not shut down, try to open a new one.
		final PushManager<T> pushManager = this;

		for (final FailedConnectionListener<? super T> listener : this.failedConnectionListeners) {

			// Handle connection failures in a separate thread in case a handler takes a long time to run
			this.listenerExecutorService.submit(new Runnable() {
				public void run() {
					listener.handleFailedConnection(pushManager, cause);
				}
			});
		}

		// As long as we're not shut down, keep trying to open a replacement connection.
		if (!this.drainingFinished) {
			this.startNewConnection();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleConnectionWritabilityChange(com.relayrides.pushy.apns.ApnsConnection, boolean)
	 */
	public void handleConnectionWritabilityChange(final ApnsConnection<T> connection, final boolean writable) {
		if (writable) {
			this.writableConnectionPool.addConnection(connection);
		} else {
			this.writableConnectionPool.removeConnection(connection);

			if (this.dispatchThread != null) {
				this.dispatchThread.interrupt();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleConnectionClosure(com.relayrides.pushy.apns.ApnsConnection)
	 */
	public void handleConnectionClosure(final ApnsConnection<T> connection) {
		// We'll remove this connection from the writable pool immediately, but will retire it from the set of active
		// connections only after its operations have finished
		this.writableConnectionPool.removeConnection(connection);

		if (this.dispatchThread != null) {
			this.dispatchThread.interrupt();
		}

		final PushManager<T> pushManager = this;

		this.listenerExecutorService.execute(new Runnable() {
			public void run() {
				try {
					connection.waitForPendingOperationsToFinish();

					// We should open a replacement connection if we're (a) running normally or (b) attempting to drain
					// the retry queue before shutting down
					if (!pushManager.drainingRetryQueue || (pushManager.drainingRetryQueue && !pushManager.retryQueue.isEmpty())) {
						pushManager.startNewConnection();
					}

					removeActiveConnection(connection);

				} catch (InterruptedException e) {
					log.warn("Interrupted while waiting for closed connection's pending operations to finish.");
				}
			}
		});
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleWriteFailure(com.relayrides.pushy.apns.ApnsConnection, com.relayrides.pushy.apns.ApnsPushNotification, java.lang.Throwable)
	 */
	public void handleWriteFailure(ApnsConnection<T> connection, T notification, Throwable cause) {
		this.retryQueue.add(notification);

		if (this.dispatchThread != null) {
			this.dispatchThread.interrupt();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleRejectedNotification(com.relayrides.pushy.apns.ApnsConnection, com.relayrides.pushy.apns.ApnsPushNotification, com.relayrides.pushy.apns.RejectedNotificationReason)
	 */
	public void handleRejectedNotification(final ApnsConnection<T> connection, final T rejectedNotification,
			final RejectedNotificationReason reason) {

		final PushManager<T> pushManager = this;

		for (final RejectedNotificationListener<? super T> listener : this.rejectedNotificationListeners) {

			// Handle the notifications in a separate thread in case a listener takes a long time to run
			this.listenerExecutorService.execute(new Runnable() {
				public void run() {
					listener.handleRejectedNotification(pushManager, rejectedNotification, reason);
				}
			});
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleUnprocessedNotifications(com.relayrides.pushy.apns.ApnsConnection, java.util.Collection)
	 */
	public void handleUnprocessedNotifications(ApnsConnection<T> connection, Collection<T> unprocessedNotifications) {
		this.retryQueue.addAll(unprocessedNotifications);

		if (this.dispatchThread != null) {
			this.dispatchThread.interrupt();
		}
	}

	private void startNewConnection() {
		synchronized (this.activeConnections) {
			final ApnsConnection<T> connection = new ApnsConnection<T>(this.environment, this.sslContext, this.eventLoopGroup, this);
			connection.connect();

			this.activeConnections.add(connection);
		}
	}

	private void removeActiveConnection(final ApnsConnection<T> connection) {
		synchronized (this.activeConnections) {
			assert this.activeConnections.remove(connection);

			if (this.activeConnections.isEmpty()) {
				this.activeConnections.notifyAll();
			}
		}
	}

	private void waitForAllOperationsToFinish(final Date deadline) throws InterruptedException {
		synchronized (this.activeConnections) {
			while (!this.activeConnections.isEmpty() && (deadline == null || deadline.getTime() > System.currentTimeMillis())) {
				if (deadline != null) {
					this.activeConnections.wait(Math.min(deadline.getTime() - System.currentTimeMillis(), 1));
				} else {
					this.activeConnections.wait();
				}
			}
		}
	}
}
