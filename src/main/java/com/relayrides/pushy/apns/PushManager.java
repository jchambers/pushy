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
import io.netty.util.concurrent.Future;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>A {@code PushManager} is the main public-facing point of interaction with APNs. {@code PushManager}s manage the
 * queue of outbound push notifications and manage connections to the various APNs servers. {@code PushManager}s should
 * always be created using the {@link PushManagerFactory} class.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 * 
 * @see PushManagerFactory
 */
public class PushManager<T extends ApnsPushNotification> {
	private final BlockingQueue<T> queue;
	private final LinkedBlockingQueue<T> retryQueue;

	private final ApnsEnvironment environment;
	private final KeyStore keyStore;
	private final char[] keyStorePassword;
	private final int concurrentConnectionCount;

	private final ArrayList<ApnsClientThread<T>> clientThreads;

	private final Vector<RejectedNotificationListener<T>> rejectedNotificationListeners;

	private final NioEventLoopGroup workerGroup;
	private final boolean shouldShutDownWorkerGroup;

	private final ExecutorService rejectedNotificationExecutorService;

	private boolean started = false;
	private boolean shutDown = false;
	private boolean shutDownFinished = false;

	private final Logger log = LoggerFactory.getLogger(PushManager.class);

	/**
	 * <p>Constructs a new {@code PushManager} that operates in the given environment with the given credentials and the
	 * given number of parallel connections to APNs. See
	 * <a href="http://developer.apple.com/library/mac/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/CommunicatingWIthAPS.html#//apple_ref/doc/uid/TP40008194-CH101-SW6">
	 * Best Practices for Managing Connections</a> for additional information.</p>
	 * 
	 * <p>This constructor may take an event loop group as an argument; if an event loop group is provided, the caller
	 * is responsible for managing the lifecycle of the group and <strong>must</strong> shut it down after shutting down
	 * this {@code PushManager}.</p>
	 * 
	 * @param environment the environment in which this {@code PushManager} operates
	 * @param keyStore A {@code KeyStore} containing the client key to present during a TLS handshake; may be
	 * {@code null} if the environment does not require TLS. The {@code KeyStore} should be loaded before being used
	 * here.
	 * @param keyStorePassword a password to unlock the given {@code KeyStore}; may be {@code null}
	 * @param concurrentConnectionCount the number of parallel connections to maintain
	 * @param workerGroup the event loop group this push manager should use for its connections to the APNs gateway and
	 * feedback service; if {@code null}, a new event loop group will be created and will be shut down automatically
	 * when the push manager is shut down. If not {@code null}, the caller <strong>must</strong> shut down the event
	 * loop group after shutting down the push manager
	 * @param queue TODO
	 */
	protected PushManager(final ApnsEnvironment environment, final KeyStore keyStore, final char[] keyStorePassword,
			final int concurrentConnectionCount, final NioEventLoopGroup workerGroup, final BlockingQueue<T> queue) {

		if (environment.isTlsRequired() && keyStore == null) {
			throw new IllegalArgumentException("Must include a non-null KeyStore for environments that require TLS.");
		}

		this.queue = queue != null ? queue : new LinkedBlockingQueue<T>();
		this.retryQueue = new LinkedBlockingQueue<T>();

		this.rejectedNotificationListeners = new Vector<RejectedNotificationListener<T>>();

		this.environment = environment;

		this.keyStore = keyStore;
		this.keyStorePassword = keyStorePassword;

		this.concurrentConnectionCount = concurrentConnectionCount;
		this.clientThreads = new ArrayList<ApnsClientThread<T>>(this.concurrentConnectionCount);

		this.rejectedNotificationExecutorService = Executors.newSingleThreadExecutor();

		if (workerGroup != null) {
			this.workerGroup = workerGroup;
			this.shouldShutDownWorkerGroup = false;
		} else {
			this.workerGroup = new NioEventLoopGroup();
			this.shouldShutDownWorkerGroup = true;
		}
	}


	/**
	 * Returns the environment in which this {@code PushManager} is operating.
	 * 
	 * @return the environment in which this {@code PushManager} is operating
	 */
	public ApnsEnvironment getEnvironment() {
		return this.environment;
	}

	/**
	 * Returns the {@code KeyStore} containing the client certificate to presented to TLS-enabled APNs servers.
	 * 
	 * @return the {@code KeyStore} containing the client certificate to presented to TLS-enabled APNs servers
	 */
	public KeyStore getKeyStore() {
		return this.keyStore;
	}

	/**
	 * Returns the key to unlock the {@code KeyStore} for this {@code PushManager}.
	 * 
	 * @return the key to unlock the {@code KeyStore} for this {@code PushManager}
	 */
	public char[] getKeyStorePassword() {
		return this.keyStorePassword;
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
			final ApnsClientThread<T> clientThread = new ApnsClientThread<T>(this);

			this.clientThreads.add(clientThread);
			clientThread.start();
		}

		this.started = true;
	}

	/**
	 * <p>Enqueues a push notification for re-transmission to the APNs service. Notifications may not be sent to APNs
	 * immediately, and delivery is not guaranteed by APNs, but notifications rejected by APNs for specific reasons
	 * will be passed to registered {@link RejectedNotificationListener}s. Notifications that are to be re-transmitted
	 * are given priority over &quot;new&quot; notifications, but are otherwise treated identically.</p>
	 * 
	 * @param notification the notification to enqueue for re-transmission
	 * 
	 * @see PushManager#registerRejectedNotificationListener(RejectedNotificationListener)
	 */
	protected void enqueuePushNotificationForRetry(final T notification) {
		this.retryQueue.add(notification);
	}

	/**
	 * <p>Enqueues a collection of push notifications for re-transmission to the APNs service. Notifications may not be
	 * sent to APNs immediately, and delivery is not guaranteed by APNs, but notifications rejected by APNs for
	 * specific reasons will be passed to registered {@link RejectedNotificationListener}s. Notifications that are to
	 * be re-transmitted are given priority over &quot;new&quot; notifications, but are otherwise treated identically.</p>
	 * 
	 * @param notifications the notifications to enqueue for re-transmission
	 * 
	 * @see PushManager#registerRejectedNotificationListener(RejectedNotificationListener)
	 */
	protected void enqueueAllNotificationsForRetry(final Collection<T> notifications) {
		this.retryQueue.addAll(notifications);
	}

	/**
	 * Indicates whether this push manager has been started and not yet shut down.
	 * 
	 * @return {@code true} if this push manager has been started and has not yet been shut down or {@code false}
	 * otherwise
	 */
	public boolean isStarted() {
		if (this.shutDown) {
			return false;
		} else {
			return this.started;
		}
	}

	/**
	 * Indicates whether this push manager has been shut down (or is in the process of shutting down).
	 * 
	 * @return {@code true} if this push manager has been shut down or is in the process of shutting down or
	 * {@code false} otherwise
	 */
	public boolean isShutDown() {
		return this.shutDown;
	}

	/**
	 * Disconnects from the APNs and gracefully shuts down all worker threads. This method will block until all client
	 * threads have shut down gracefully.
	 * 
	 * @return a list of notifications not sent before the {@code PushManager} shut down
	 * 
	 * @throws InterruptedException if interrupted while waiting for worker threads to exit cleanly
	 * @throws IllegalStateException if this method is called before the push manager has been started
	 */
	public synchronized List<T> shutdown() throws InterruptedException {
		return this.shutdown(0);
	}

	/**
	 * Disconnects from the APNs and gracefully shuts down all worker threads. This method will wait until the given
	 * timeout expires for client threads to shut down gracefully, and will then instruct them to shut down as soon
	 * as possible (and will block until shutdown is complete). Note that the returned list of undelivered push
	 * notifications may not be accurate in cases where the timeout elapsed before the client threads shut down.
	 * 
	 * @param timeout the timeout, in milliseconds, after which client threads should be shut down as quickly as possible
	 * 
	 * @return a list of notifications not sent before the {@code PushManager} shut down
	 * 
	 * @throws InterruptedException if interrupted while waiting for worker threads to exit cleanly
	 * @throws IllegalStateException if this method is called before the push manager has been started
	 */
	public synchronized List<T> shutdown(long timeout) throws InterruptedException {
		if (this.shutDown) {
			log.warn("Push manager has already been shut down; shutting down multiple times is harmless, but may "
					+ "indicate a problem elsewhere.");
		}

		if (this.shutDownFinished) {
			// We COULD throw an IllegalStateException here, but it seems unnecessary when we could just silently return
			// the same result without harm.
			return new ArrayList<T>(this.queue);
		}

		if (!this.isStarted()) {
			throw new IllegalStateException("Push manager has not yet been started and cannot be shut down.");
		}

		this.shutDown = true;

		for (final ApnsClientThread<T> clientThread : this.clientThreads) {
			clientThread.requestShutdown();
		}

		if (timeout > 0) {
			final long deadline = System.currentTimeMillis() + timeout;

			for (final ApnsClientThread<T> clientThread : this.clientThreads) {
				final long remainingTimeout = deadline - System.currentTimeMillis();

				if (remainingTimeout <= 0) {
					break;
				}

				clientThread.join(remainingTimeout);
			}

			for (final ApnsClientThread<T> clientThread : this.clientThreads) {
				if (clientThread.isAlive()) {
					clientThread.shutdownImmediately();
				}
			}
		}

		for (final ApnsClientThread<T> clientThread : this.clientThreads) {
			clientThread.join();
		}

		this.rejectedNotificationListeners.clear();
		this.rejectedNotificationExecutorService.shutdown();

		if (this.shouldShutDownWorkerGroup) {
			if (!this.workerGroup.isShutdown()) {
				final Future<?> workerShutdownFuture = this.workerGroup.shutdownGracefully();
				workerShutdownFuture.await();
			}
		}

		this.shutDownFinished = true;

		final ArrayList<T> unsentNotifications = new ArrayList<T>();

		unsentNotifications.addAll(this.retryQueue);
		unsentNotifications.addAll(this.getQueue());

		return unsentNotifications;
	}

	/**
	 * <p>Registers a listener for notifications rejected by APNs for specific reasons. Note that listeners are stored
	 * as strong references; all listeners are automatically un-registered when the push manager is shut down, but
	 * failing to unregister a listener manually or to shut down the push manager may cause a memory leak.</p>
	 * 
	 * @param listener the listener to register
	 * 
	 * @throws IllegalStateException if this push manager has already been shut down
	 * 
	 * @see PushManager#unregisterRejectedNotificationListener(RejectedNotificationListener)
	 */
	public void registerRejectedNotificationListener(final RejectedNotificationListener<T> listener) {
		if (this.shutDown) {
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
	public boolean unregisterRejectedNotificationListener(final RejectedNotificationListener<T> listener) {
		return this.rejectedNotificationListeners.remove(listener);
	}

	protected void notifyListenersOfRejectedNotification(final T notification, final RejectedNotificationReason reason) {
		for (final RejectedNotificationListener<T> listener : this.rejectedNotificationListeners) {

			// Handle the notifications in a separate thread in case a listener takes a long time to run
			this.rejectedNotificationExecutorService.submit(new Runnable() {
				public void run() {
					listener.handleRejectedNotification(notification, reason);
				}
			});
		}
	}

	/**
	 * <p>Returns the queue of messages to be sent to the APNs gateway. Callers should add notifications to this queue
	 * directly to send notifications. Notifications will be removed from this queue by Pushy when a send attempt is
	 * started, but no guarantees are made as to when the notification will actually be sent. Successful delivery is
	 * neither guaranteed nor acknowledged by the APNs gateway. Notifications rejected by APNs for specific reasons
	 * will be passed to registered {@link RejectedNotificationListener}s, and notifications that could not be sent due
	 * to temporary I/O problems will be scheduled for re-transmission in a separate, internal queue.</p>
	 * 
	 * <p>Notifications in this queue will only be consumed when the {@code PushManager} is running and has active
	 * connections and when the internal &quot;retry queue&quot; is empty.</p>
	 * 
	 * @return the queue of new notifications to send to the APNs gateway
	 * 
	 * @see PushManager#registerRejectedNotificationListener(RejectedNotificationListener)
	 */
	public BlockingQueue<T> getQueue() {
		return this.queue;
	}

	protected LinkedBlockingQueue<T> getRetryQueue() {
		return this.retryQueue;
	}

	protected NioEventLoopGroup getWorkerGroup() {
		return this.workerGroup;
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
	 */
	public List<ExpiredToken> getExpiredTokens() throws InterruptedException {
		return this.getExpiredTokens(1, TimeUnit.SECONDS);
	}

	/**
	 * <p>Queries the APNs feedback service for expired tokens using the given timeout. Be warned that this is a
	 * <strong>destructive operation</strong>. According to Apple's documentation:</p>
	 * 
	 * <blockquote>The feedback service’s list is cleared after you read it. Each time you connect to the feedback
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
	 * @throws IllegalStateException if this push manager has not been started yet or has already been shut down
	 */
	public List<ExpiredToken> getExpiredTokens(final long timeout, final TimeUnit timeoutUnit) throws InterruptedException {
		if (!this.isStarted()) {
			throw new IllegalStateException("Push manager has not been started yet.");
		}

		if (this.isShutDown()) {
			throw new IllegalStateException("Push manager has already been shut down.");
		}

		return new FeedbackServiceClient(this).getExpiredTokens(timeout, timeoutUnit);
	}
}
