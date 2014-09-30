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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Push managers manage connections to the APNs gateway and send notifications from their queue. Push managers are
 * the main public-facing point of interaction with Pushy.</p>
 *
 * <h2>Queues</h2>
 *
 * <p>A push manager has two queues: the public queue through which callers add notifications to be sent, and a private,
 * internal &quot;retry queue.&quot; Callers send push notifications by adding them to the push manager's public queue
 * (see {@link PushManager#getQueue()}). The push manager will take and notifications from the public queue as quickly
 * as it is able to do so, and will never put notifications back in the public queue. Callers are free to manipulate the
 * public queue however they see fit.</p>
 *
 * <p>If, for any reason other than a permanent rejection, a notification could not be delivered, it will be returned to
 * the push manager's internal retry queue. The push manager will always try to drain its retry queue before taking new
 * notifications from the public queue.</p>
 *
 * <h2>Shutting down</h2>
 *
 * <p>A push manager can be shut down with or without a timeout, though shutting down without a timeout provides
 * stronger guarantees with regard to the state of sent notifications. Regardless of whether a timeout is specified,
 * push managers will stop taking notifications from the public queue as soon the shutdown process has started. Push
 * managers shut down by asking all of their connections to shut down gracefully by sending a known-bad notification to
 * the APNs gateway. The push manager will restore closed connections and keep trying to send notifications from its
 * internal retry queue until either the queue is empty or the timeout expires. If the timeout expires while there are
 * still open connections, all remaining connections are closed immediately.</p>
 *
 * <p>When shutting down without a timeout, it is guaranteed that the push manager's internal retry queue will be empty
 * and all sent notifications will have reached and been processed by the APNs gateway. Any notifications not rejected
 * by the gateway by the time the shutdown process completes will have been accepted by the gateway (though no
 * guarantees are made that they will ever be delivered to the destination device).</p>
 *
 * <h2>Error handling</h2>
 *
 * <p>Callers may register listeners to handle notifications permanently rejected by the APNs gateway and to handle
 * failed attempts to connect to the gateway.</p>
 *
 * <p>When a notification is rejected by the APNs gateway, the rejection should be considered permanent and callers
 * should not try to resend the notification. When a connection fails, the push manager will report the failure to
 * registered listeners, but will continue trying to connect until shut down. Callers should shut down the push manager
 * in the event of a failure unlikely to be resolved by retrying the connection (the most common case is an
 * {@link SSLHandshakeException}, which usually indicates a certificate problem of some kind).</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 * @see PushManager#getQueue()
 */
public class PushManager<T extends ApnsPushNotification> implements ApnsConnectionListener<T>, FeedbackServiceListener {
	private final BlockingQueue<T> queue;
	private final LinkedBlockingQueue<T> retryQueue = new LinkedBlockingQueue<T>();;

	private final ApnsEnvironment environment;
	private final SSLContext sslContext;

	private final PushManagerConfiguration configuration;

	private final String name;
	private static final AtomicInteger pushManagerCounter = new AtomicInteger(0);
	private int connectionCounter = 0;
	private int feedbackConnectionCounter = 0;

	private final HashSet<ApnsConnection<T>> activeConnections = new HashSet<ApnsConnection<T>>();
	private final ApnsConnectionPool<T> writableConnectionPool = new ApnsConnectionPool<T>();

	private final Object feedbackConnectionMonitor = new Object();
	private FeedbackServiceConnection feedbackConnection;
	private List<ExpiredToken> expiredTokens;

	private final List<RejectedNotificationListener<? super T>> rejectedNotificationListeners =
			new ArrayList<RejectedNotificationListener<? super T>>();

	private final List<FailedConnectionListener<? super T>> failedConnectionListeners =
			new ArrayList<FailedConnectionListener<? super T>>();

	private final List<ExpiredTokenListener<? super T>> expiredTokenListeners =
			new ArrayList<ExpiredTokenListener<? super T>>();

	private Thread dispatchThread;
	private boolean dispatchThreadShouldContinue = true;

	private final NioEventLoopGroup eventLoopGroup;
	private final boolean shouldShutDownEventLoopGroup;

	private final ExecutorService listenerExecutorService;
	private final boolean shouldShutDownListenerExecutorService;

	private boolean shutDownStarted = false;
	private boolean shutDownFinished = false;

	private static final Logger log = LoggerFactory.getLogger(PushManager.class);

	private static class DispatchThreadExceptionHandler<T extends ApnsPushNotification> implements UncaughtExceptionHandler {
		private final Logger log = LoggerFactory.getLogger(DispatchThreadExceptionHandler.class);

		final PushManager<T> manager;

		public DispatchThreadExceptionHandler(final PushManager<T> manager) {
			this.manager = manager;
		}

		@Override
		public void uncaughtException(final Thread t, final Throwable e) {
			log.error("Dispatch thread for {} died unexpectedly. Please file a bug with the exception details.",
					this.manager.name, e);

			if (this.manager.isStarted()) {
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
	 * <p>This constructor may take an event loop group as an argument. The push manager's event loop group will be
	 * used for IO operations in all connections created by this push manager. If an event loop group is provided, the
	 * caller is responsible for managing the lifecycle of the group and <strong>must</strong> shut it down after
	 * shutting down this {@code PushManager}.</p>
	 *
	 * <p>This constructor may also take an {@link java.util.concurrent.ExecutorService} as an argument. The executor
	 * service is used to dispatch notifications to registered listeners. If a non-{@code null} executor service is
	 * provided, callers <strong>must</strong> shut down the executor service after shutting down all
	 * {@code PushManager} instances that use that executor service.</p>
	 *
	 * @param environment the environment in which this {@code PushManager} operates; must not be {@code null}
	 * @param sslContext the SSL context in which APNs connections controlled by this {@code PushManager} will operate;
	 * must not be {@code null}
	 * @param eventLoopGroup the event loop group this push manager should use for its connections to the APNs gateway and
	 * feedback service; if {@code null}, a new event loop group will be created and will be shut down automatically
	 * when the push manager is shut down. If not {@code null}, the caller <strong>must</strong> shut down the event
	 * loop group after shutting down the push manager.
	 * @param listenerExecutorService the executor service this push manager should use to dispatch notifications to
	 * registered listeners. If {@code null}, a new single-thread executor service will be created and will be shut
	 * down automatically with the push manager is shut down. If not {@code null}, the caller <strong>must</strong>
	 * shut down the executor service after shutting down the push manager.
	 * @param queue the queue to be used to pass new notifications to this push manager; if {@code null}, the new push
	 * manager will create its own queue
	 * @param configuration the set of configuration options to use for this push manager and the connections it
	 * creates. The configuration object is copied and changes to the original object will not propagate to the push
	 * manager after creation. Must not be {@code null}.
	 * @param name a human-readable name for this push manager; if {@code null}, a default name will be used
	 */
	public PushManager(final ApnsEnvironment environment, final SSLContext sslContext,
			final NioEventLoopGroup eventLoopGroup, final ExecutorService listenerExecutorService,
			final BlockingQueue<T> queue, final PushManagerConfiguration configuration, final String name) {

		this.queue = queue != null ? queue : new LinkedBlockingQueue<T>();

		if (environment == null) {
			throw new NullPointerException("Environment must not be null.");
		}

		this.environment = environment;

		if (sslContext == null) {
			throw new NullPointerException("SSL context must not be null.");
		}

		this.sslContext = sslContext;

		if (configuration == null) {
			throw new NullPointerException("Configuration object must not be null.");
		}

		this.configuration = new PushManagerConfiguration(configuration);
		this.name = name == null ? String.format("PushManager-%d", PushManager.pushManagerCounter.getAndIncrement()) : name;

		if (eventLoopGroup != null) {
			this.eventLoopGroup = eventLoopGroup;
			this.shouldShutDownEventLoopGroup = false;
		} else {
			// Never use more threads than concurrent connections (Netty binds a channel to a single thread, so the
			// excess threads would always go unused)
			final int threadCount = Math.min(this.configuration.getConcurrentConnectionCount(), Runtime.getRuntime().availableProcessors() * 2);

			this.eventLoopGroup = new NioEventLoopGroup(threadCount);
			this.shouldShutDownEventLoopGroup = true;
		}

		if (listenerExecutorService != null) {
			this.listenerExecutorService = listenerExecutorService;
			this.shouldShutDownListenerExecutorService = false;
		} else {
			this.listenerExecutorService = Executors.newSingleThreadExecutor();
			this.shouldShutDownListenerExecutorService = true;
		}
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

		log.info("{} starting.", this.name);

		for (int i = 0; i < this.configuration.getConcurrentConnectionCount(); i++) {
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

			@Override
			public void run() {
				while (dispatchThreadShouldContinue) {
					try {
						final ApnsConnection<T> connection = writableConnectionPool.getNextConnection();
						final T notificationToRetry = retryQueue.poll();

						if (notificationToRetry != null) {
							connection.sendNotification(notificationToRetry);
						} else {
							if (shutDownStarted) {
								// We're trying to drain the retry queue before shutting down, and the retry queue is
								// now empty. Close the connection and see if it stays that way.
								connection.shutdownGracefully();
								writableConnectionPool.removeConnection(connection);
							} else {
								// We'll park here either until a new notification is available from the outside or until
								// something shows up in the retry queue, at which point we'll be interrupted.
								connection.sendNotification(queue.take());
							}
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
		return this.shutDownStarted;
	}

	/**
	 * <p>Disconnects from APNs and gracefully shuts down all connections. As soon as this method is called, the push
	 * manager will stop taking notifications from the public queue. This method will block until the internal retry queue
	 * has been emptied and until all connections have shut down gracefully. Calling this method is identical to calling
	 * {@link PushManager#shutdown(long)} with a timeout of {@code 0}.</p>
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
	 * <p>Disconnects from the APNs and gracefully shuts down all connections. As soon as this method is called, the
	 * push manager will stop taking notifications from the public queue. This method will wait until either the given
	 * timeout expires or until the internal retry queue has been emptied and connections have closed gracefully. If the
	 * timeout expires, the push manager will close all connections immediately.</p>
	 *
	 * <p>This method returns the notifications that are still in the internal retry queue by the time this push manager
	 * has shut down. If this method is called with a non-zero timeout, a collection of notifications still in the push
	 * manager's internal retry queue will be returned. The returned collection will <em>not</em> contain notifications
	 * in the public queue (since callers can work with the public queue directly). When shutting down with a non-zero
	 * timeout, no guarantees are made that notifications that were sent (i.e. are in neither the public queue nor the
	 * retry queue) were actually received or processed by the APNs gateway.</p>
	 *
	 * <p>If called with a timeout of {@code 0}, the returned collection of unsent notifications will be empty. By the
	 * time this method exits, all notifications taken from the public queue are guaranteed to have been delivered to
	 * the APNs gateway and either accepted or rejected (i.e. the state of all sent notifications is known).</p>
	 *
	 * @param timeout the timeout, in milliseconds, after which client threads should be shut down as quickly as
	 * possible; if {@code 0}, this method will wait indefinitely for the retry queue to empty and connections to close
	 *
	 * @return a list of notifications not sent before the {@code PushManager} shut down
	 *
	 * @throws InterruptedException if interrupted while waiting for connections to close cleanly
	 * @throws IllegalStateException if this method is called before the push manager has been started
	 */
	public synchronized List<T> shutdown(long timeout) throws InterruptedException {
		if (this.isShutDown()) {
			log.warn("{} has already been shut down; shutting down multiple times is harmless, but may "
					+ "indicate a problem elsewhere.", this.name);
		} else {
			log.info("{} shutting down.", this.name);
		}

		if (this.shutDownFinished) {
			// We COULD throw an IllegalStateException here, but it seems unnecessary when we could just silently return
			// the same result without harm.
			return new ArrayList<T>(this.retryQueue);
		}

		if (!this.isStarted()) {
			throw new IllegalStateException("Push manager has not yet been started and cannot be shut down.");
		}

		this.shutDownStarted = true;

		synchronized (this.feedbackConnectionMonitor) {
			if (this.feedbackConnection != null) {
				this.feedbackConnection.shutdownImmediately();
			}
		}

		this.dispatchThread.interrupt();

		final Date deadline = timeout > 0 ? new Date(System.currentTimeMillis() + timeout) : null;

		// The dispatch thread will close connections when the retry queue is empty
		this.waitForAllConnectionsToFinish(deadline);

		this.dispatchThreadShouldContinue = false;
		this.dispatchThread.interrupt();
		this.dispatchThread.join();

		if (deadline == null) {
			assert this.retryQueue.isEmpty();
			assert this.activeConnections.isEmpty();
		}

		synchronized (this.activeConnections) {
			for (final ApnsConnection<T> connection : this.activeConnections) {
				connection.shutdownImmediately();
			}
		}

		synchronized (this.rejectedNotificationListeners) {
			this.rejectedNotificationListeners.clear();
		}

		synchronized (this.failedConnectionListeners) {
			this.failedConnectionListeners.clear();
		}

		synchronized (this.expiredTokenListeners) {
			this.expiredTokenListeners.clear();
		}

		if (this.shouldShutDownListenerExecutorService) {
			this.listenerExecutorService.shutdown();
		}

		if (this.shouldShutDownEventLoopGroup) {
			this.eventLoopGroup.shutdownGracefully().await();
		}

		this.shutDownFinished = true;

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

		synchronized (this.rejectedNotificationListeners) {
			this.rejectedNotificationListeners.add(listener);
		}
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
		synchronized (this.rejectedNotificationListeners) {
			return this.rejectedNotificationListeners.remove(listener);
		}
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

		synchronized (this.failedConnectionListeners) {
			this.failedConnectionListeners.add(listener);
		}
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
		synchronized (this.failedConnectionListeners) {
			return this.failedConnectionListeners.remove(listener);
		}
	}

	/**
	 * Returns the human-readable name of this push manager.
	 *
	 * @return the human-readable name of this push manager
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * <p>Returns the queue of messages to be sent to the APNs gateway. Callers should add notifications to this queue
	 * directly to send notifications. Notifications will be removed from this queue by Pushy when a send attempt is
	 * started, but are not guaranteed to have reached the APNs gateway until the push manager has been shut down
	 * without a timeout (see {@link PushManager#shutdown(long)}). Successful delivery is not acknowledged by the APNs
	 * gateway. Notifications rejected by APNs for specific reasons will be passed to registered
	 * {@link RejectedNotificationListener}s, and notifications that could not be sent due to temporary I/O problems
	 * will be scheduled for re-transmission in a separate, internal queue.</p>
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
	 * Registers a listener for expired tokens received from the feedback service. Expired tokens will be passed to
	 * registered listeners some time after a call to {@link PushManager#requestExpiredTokens()}.
	 *
	 * @param listener the listener to register
	 *
	 * @throws IllegalStateException if this push manager has already been shut down
	 *
	 * @see PushManager#unregisterExpiredTokenListener(ExpiredTokenListener)
	 * @see PushManager#requestExpiredTokens()
	 */
	public void registerExpiredTokenListener(final ExpiredTokenListener<? super T> listener) {
		if (this.isShutDown()) {
			throw new IllegalStateException("Expired token listeners may not be registered after a push manager has been shut down.");
		}

		synchronized (this.expiredTokenListeners) {
			this.expiredTokenListeners.add(listener);
		}
	}

	/**
	 * <p>Un-registers an expired token listener.</p>
	 *
	 * @param listener the listener to un-register
	 *
	 * @return {@code true} if the given listener was registered with this push manager and removed or {@code false} if
	 * the listener was not already registered with this push manager
	 */
	public boolean unregisterExpiredTokenListener(final ExpiredTokenListener<? super T> listener) {
		synchronized (this.expiredTokenListeners) {
			return this.expiredTokenListeners.remove(listener);
		}
	}

	/**
	 * <p>Begins an asynchronous attempt to get a list of expired tokens from the feedback service. Be warned that this
	 * is a <strong>destructive operation</strong>. According to Apple's documentation:</p>
	 *
	 * <blockquote>The feedback service’s list is cleared after you read it. Each time you connect to the feedback
	 * service, the information it returns lists only the failures that have happened since you last
	 * connected.</blockquote>
	 *
	 * <p>When a list of expired tokens has been gathered, registered expired token listeners will be notified
	 * (see {@link PushManager#registerExpiredTokenListener(ExpiredTokenListener)}). If a feedback polling attempt
	 * fails, registered failed connection listeners will be notified.</p>
	 *
	 * @see PushManager#registerExpiredTokenListener(ExpiredTokenListener)
	 * @see PushManager#registerFailedConnectionListener(FailedConnectionListener)
	 */
	public synchronized void requestExpiredTokens() {
		if (!this.isStarted()) {
			throw new IllegalStateException("Push manager has not been started yet.");
		}

		if (this.isShutDown()) {
			throw new IllegalStateException("Push manager has already been shut down.");
		}

		synchronized (this.feedbackConnectionMonitor) {
			// If we already have a feedback connection in play, let it finish
			if (this.feedbackConnection == null) {
				this.expiredTokens = new ArrayList<ExpiredToken>();

				this.feedbackConnection = new FeedbackServiceConnection(
						this.environment, this.sslContext, this.eventLoopGroup,
						this.configuration.getFeedbackConnectionConfiguration(), this,
						String.format("%s-feedbackConnection-%d", this.name, this.feedbackConnectionCounter++));

				this.feedbackConnection.connect();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.FeedbackServiceListener#handleConnectionSuccess(com.relayrides.pushy.apns.FeedbackServiceConnection)
	 */
	@Override
	public void handleConnectionSuccess(final FeedbackServiceConnection connection) {
		log.trace("Feedback connection succeeded: {}", connection);
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.FeedbackServiceListener#handleConnectionFailure(com.relayrides.pushy.apns.FeedbackServiceConnection, java.lang.Throwable)
	 */
	@Override
	public void handleConnectionFailure(final FeedbackServiceConnection connection, final Throwable cause) {
		log.trace("Feedback connection failed: {}", connection, cause);

		synchronized (this.feedbackConnectionMonitor) {
			this.feedbackConnection = null;
		}

		synchronized (this.failedConnectionListeners) {
			final PushManager<T> pushManager = this;

			for (final FailedConnectionListener<? super T> listener : this.failedConnectionListeners) {

				// Handle connection failures in a separate thread in case a handler takes a long time to run
				this.listenerExecutorService.submit(new Runnable() {
					@Override
					public void run() {
						listener.handleFailedConnection(pushManager, cause);
					}
				});
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.FeedbackServiceListener#handleExpiredToken(com.relayrides.pushy.apns.FeedbackServiceConnection, com.relayrides.pushy.apns.ExpiredToken)
	 */
	@Override
	public void handleExpiredToken(final FeedbackServiceConnection connection, final ExpiredToken token) {
		log.trace("Received expired token {} from feedback connection {}.", token, connection);
		this.expiredTokens.add(token);
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.FeedbackServiceListener#handleConnectionClosure(com.relayrides.pushy.apns.FeedbackServiceConnection)
	 */
	@Override
	public void handleConnectionClosure(final FeedbackServiceConnection connection) {
		log.trace("Feedback connection closed: {}", connection);

		final PushManager<T> pushManager = this;
		final List<ExpiredToken> expiredTokens = new ArrayList<ExpiredToken>(this.expiredTokens);

		synchronized (this.expiredTokenListeners) {
			for (final ExpiredTokenListener<? super T> listener : this.expiredTokenListeners) {
				this.listenerExecutorService.submit(new Runnable() {

					@Override
					public void run() {
						listener.handleExpiredTokens(pushManager, expiredTokens);
					}});
			}
		}

		synchronized (this.feedbackConnectionMonitor) {
			this.feedbackConnection = null;
			this.expiredTokens = null;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleConnectionSuccess(com.relayrides.pushy.apns.ApnsConnection)
	 */
	@Override
	public void handleConnectionSuccess(final ApnsConnection<T> connection) {
		log.trace("Connection succeeded: {}", connection);

		if (this.dispatchThreadShouldContinue) {
			this.writableConnectionPool.addConnection(connection);
		} else {
			// There's no dispatch thread to use this connection, so shut it down immediately
			connection.shutdownImmediately();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleConnectionFailure(com.relayrides.pushy.apns.ApnsConnection, java.lang.Throwable)
	 */
	@Override
	public void handleConnectionFailure(final ApnsConnection<T> connection, final Throwable cause) {

		log.trace("Connection failed: {}", connection, cause);

		this.removeActiveConnection(connection);

		synchronized (this.failedConnectionListeners) {
			final PushManager<T> pushManager = this;

			for (final FailedConnectionListener<? super T> listener : this.failedConnectionListeners) {

				// Handle connection failures in a separate thread in case a handler takes a long time to run
				this.listenerExecutorService.submit(new Runnable() {
					@Override
					public void run() {
						listener.handleFailedConnection(pushManager, cause);
					}
				});
			}
		}

		// As long as we're not shut down, try to open a replacement connection.
		if (this.shouldReplaceClosedConnection()) {
			this.startNewConnection();
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
			this.writableConnectionPool.addConnection(connection);
		} else {
			this.writableConnectionPool.removeConnection(connection);
			this.dispatchThread.interrupt();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleConnectionClosure(com.relayrides.pushy.apns.ApnsConnection)
	 */
	@Override
	public void handleConnectionClosure(final ApnsConnection<T> connection) {

		log.trace("Connection closed: {}", connection);

		this.writableConnectionPool.removeConnection(connection);
		this.dispatchThread.interrupt();

		final PushManager<T> pushManager = this;

		this.listenerExecutorService.execute(new Runnable() {
			@Override
			public void run() {
				try {
					connection.waitForPendingWritesToFinish();

					if (pushManager.shouldReplaceClosedConnection()) {
						pushManager.startNewConnection();
					}

					removeActiveConnection(connection);
				} catch (InterruptedException e) {
					log.warn("{} interrupted while waiting for closed connection's pending operations to finish.",
							pushManager.name);
				}
			}
		});
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleWriteFailure(com.relayrides.pushy.apns.ApnsConnection, com.relayrides.pushy.apns.ApnsPushNotification, java.lang.Throwable)
	 */
	@Override
	public void handleWriteFailure(ApnsConnection<T> connection, T notification, Throwable cause) {
		this.retryQueue.add(notification);
		this.dispatchThread.interrupt();
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleRejectedNotification(com.relayrides.pushy.apns.ApnsConnection, com.relayrides.pushy.apns.ApnsPushNotification, com.relayrides.pushy.apns.RejectedNotificationReason)
	 */
	@Override
	public void handleRejectedNotification(final ApnsConnection<T> connection, final T rejectedNotification,
			final RejectedNotificationReason reason) {

		log.trace("{} rejected {}: {}", connection, rejectedNotification, reason);

		final PushManager<T> pushManager = this;

		synchronized (this.rejectedNotificationListeners) {
			for (final RejectedNotificationListener<? super T> listener : this.rejectedNotificationListeners) {

				// Handle the notifications in a separate thread in case a listener takes a long time to run
				this.listenerExecutorService.execute(new Runnable() {
					@Override
					public void run() {
						listener.handleRejectedNotification(pushManager, rejectedNotification, reason);
					}
				});
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.relayrides.pushy.apns.ApnsConnectionListener#handleUnprocessedNotifications(com.relayrides.pushy.apns.ApnsConnection, java.util.Collection)
	 */
	@Override
	public void handleUnprocessedNotifications(ApnsConnection<T> connection, Collection<T> unprocessedNotifications) {

		log.trace("{} returned {} unprocessed notifications", connection, unprocessedNotifications.size());

		this.retryQueue.addAll(unprocessedNotifications);

		this.dispatchThread.interrupt();
	}

	private void startNewConnection() {
		synchronized (this.activeConnections) {
			final ApnsConnection<T> connection = new ApnsConnection<T>(this.environment, this.sslContext,
					this.eventLoopGroup, this.configuration.getConnectionConfiguration(), this,
					String.format("%s-connection-%d", this.name, this.connectionCounter++));

			connection.connect();

			this.activeConnections.add(connection);
		}
	}

	private void removeActiveConnection(final ApnsConnection<T> connection) {
		synchronized (this.activeConnections) {
			final boolean removedConnection = this.activeConnections.remove(connection);
			assert removedConnection;

			if (this.activeConnections.isEmpty()) {
				this.activeConnections.notifyAll();
			}
		}
	}

	private void waitForAllConnectionsToFinish(final Date deadline) throws InterruptedException {
		synchronized (this.activeConnections) {
			while (!this.activeConnections.isEmpty() && !PushManager.hasDeadlineExpired(deadline)) {
				if (deadline != null) {
					this.activeConnections.wait(PushManager.getMillisToWaitForDeadline(deadline));
				} else {
					this.activeConnections.wait();
				}
			}
		}
	}

	private static long getMillisToWaitForDeadline(final Date deadline) {
		return Math.max(deadline.getTime() - System.currentTimeMillis(), 1);
	}

	private static boolean hasDeadlineExpired(final Date deadline) {
		if (deadline != null) {
			return System.currentTimeMillis() > deadline.getTime();
		} else {
			return false;
		}
	}

	private boolean shouldReplaceClosedConnection() {
		if (this.shutDownStarted) {
			if (this.dispatchThreadShouldContinue) {
				// We're shutting down, but the dispatch thread is still working to drain the retry queue. Replace
				// closed connections until the retry queue is empty.
				return !this.retryQueue.isEmpty();
			} else {
				// If this dispatch thread should stop, there's nothing to make use of the connections
				return false;
			}
		} else {
			// We always want to replace closed connections if we're running normally
			return true;
		}
	}
}
