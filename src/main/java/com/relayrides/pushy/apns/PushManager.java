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

import java.lang.ref.WeakReference;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * <p>A {@code PushManager} is the main public-facing point of interaction with APNs. {@code PushManager}s manage the
 * queue of outbound push notifications and manage connections to the various APNs servers.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class PushManager<T extends ApnsPushNotification> {
	private final BlockingQueue<T> queue;
	
	private final ApnsEnvironment environment;
	private final KeyStore keyStore;
	private final char[] keyStorePassword;
	private final int concurrentConnections;
	
	private final ArrayList<ApnsClientThread<T>> clientThreads;
	
	private final ArrayList<WeakReference<RejectedNotificationListener<T>>> rejectedNotificationListeners;
	
	private final NioEventLoopGroup workerGroup;
	private final boolean shouldShutDownWorkerGroup;
	
	private final ExecutorService rejectedNotificationExecutorService;
	
	/**
	 * Constructs a new {@code PushManager} that operates in the given environment with the given credentials, a single
	 * connection to APNs, and a default event loop group.
	 * 
	 * @param environment the environment in which this {@code PushManager} operates
	 * @param keyStore A {@code KeyStore} containing the client key to present during a TLS handshake; may be
	 * {@code null} if the environment does not require TLS. The {@code KeyStore} should be loaded before being used
	 * here.
	 * @param keyStorePassword a password to unlock the given {@code KeyStore}; may be {@code null}
	 */
	public PushManager(final ApnsEnvironment environment, final KeyStore keyStore, final char[] keyStorePassword) {
		this(environment, keyStore, keyStorePassword, 1);
	}
	
	/**
	 * <p>Constructs a new {@code PushManager} that operates in the given environment with the given credentials, the
	 * given number of parallel connections to APNs, and a default event loop group. See
	 * <a href="http://developer.apple.com/library/mac/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/CommunicatingWIthAPS.html#//apple_ref/doc/uid/TP40008194-CH101-SW6">
	 * Best Practices for Managing Connections</a> for additional information.</p>
	 * 
	 * @param environment the environment in which this {@code PushManager} operates
	 * @param keyStore A {@code KeyStore} containing the client key to present during a TLS handshake; may be
	 * {@code null} if the environment does not require TLS. The {@code KeyStore} should be loaded before being used
	 * here.
	 * @param keyStorePassword a password to unlock the given {@code KeyStore}; may be {@code null}
	 * @param concurrentConnections the number of parallel connections to open to APNs
	 */
	public PushManager(final ApnsEnvironment environment, final KeyStore keyStore, final char[] keyStorePassword, final int concurrentConnections) {
		this(environment, keyStore, keyStorePassword, concurrentConnections, null);
	}
	
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
	 * @param concurrentConnections the number of parallel connections to open to APNs
	 * @param workerGroup the event loop group this push manager should use for its connections to the APNs gateway and
	 * feedback service; if {@code null}, a new event loop group will be created and will be shut down automatically
	 * when the push manager is shut down. If not {@code null}, the caller <strong>must</strong> shut down the event
	 * loop group after shutting down the push manager
	 */
	public PushManager(final ApnsEnvironment environment, final KeyStore keyStore, final char[] keyStorePassword, final int concurrentConnections, final NioEventLoopGroup workerGroup) {
		
		if (environment.isTlsRequired() && keyStore == null) {
			throw new IllegalArgumentException("Must include a non-null KeyStore for environments that require TLS.");
		}
		
		this.queue = new LinkedBlockingQueue<T>();
		this.rejectedNotificationListeners = new ArrayList<WeakReference<RejectedNotificationListener<T>>>();
		
		this.environment = environment;
		
		this.keyStore = keyStore;
		this.keyStorePassword = keyStorePassword;
		
		this.concurrentConnections = concurrentConnections;
		this.clientThreads = new ArrayList<ApnsClientThread<T>>(this.concurrentConnections);
		
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
	 * Opens all connections to APNs and prepares to send push notifications. Note that enqueued push notifications
	 * will <strong>not</strong> be sent until this method is called.
	 */
	public synchronized void start() {
		for (int i = 0; i < this.concurrentConnections; i++) {
			final ApnsClientThread<T> clientThread = new ApnsClientThread<T>(this);
			
			this.clientThreads.add(clientThread);
			clientThread.start();
		}
	}
	
	/**
	 * <p>Enqueues a push notification for transmission to the APNs service. Notifications may not be sent to APNs
	 * immediately, and delivery is not guaranteed by APNs, but notifications rejected by APNs for specific reasons
	 * will be passed to registered {@link RejectedNotificationListener}s.</p>
	 * 
	 * @param notification the notification to enqueue
	 * 
	 * @see PushManager#registerRejectedNotificationListener(RejectedNotificationListener)
	 */
	public void enqueuePushNotification(final T notification) {
		this.queue.add(notification);
	}
	
	/**
	 * <p>Enqueues a collection of push notifications for transmission to the APNs service. Notifications may not be
	 * sent to APNs immediately, and delivery is not guaranteed by APNs, but notifications rejected by APNs for
	 * specific reasons will be passed to registered {@link RejectedNotificationListener}s.</p>
	 * 
	 * @param notifications the notifications to enqueue
	 * 
	 * @see PushManager#registerRejectedNotificationListener(RejectedNotificationListener)
	 */
	public void enqueueAllNotifications(final Collection<T> notifications) {
		this.queue.addAll(notifications);
	}
	
	/**
	 * Disconnects from the APNs and gracefully shuts down all worker threads. This method will block until all client
	 * threads have shut down gracefully.
	 * 
	 * @return a list of notifications not sent before the {@code PushManager} shut down
	 * 
	 * @throws InterruptedException if interrupted while waiting for worker threads to exit cleanly
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
	 * @param the timeout, in milliseconds, after which client threads should be shut down as quickly as possible
	 * 
	 * @return a list of notifications not sent before the {@code PushManager} shut down
	 * 
	 * @throws InterruptedException if interrupted while waiting for worker threads to exit cleanly
	 */
	public synchronized List<T> shutdown(long timeout) throws InterruptedException {
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
		
		this.rejectedNotificationExecutorService.shutdown();
		
		if (this.shouldShutDownWorkerGroup) {
			final Future<?> workerShutdownFuture = this.workerGroup.shutdownGracefully();
			workerShutdownFuture.await();
		}
		
		return new ArrayList<T>(this.queue);
	}
	
	/**
	 * <p>Registers a listener for notifications rejected by APNs for specific reasons. Note that listeners are stored
	 * as weak references, so callers should maintain a reference to listeners to prevent them from being garbage
	 * collected.</p>
	 * 
	 * @param listener the listener to register
	 */
	public synchronized void registerRejectedNotificationListener(final RejectedNotificationListener<T> listener) {
		this.rejectedNotificationListeners.add(new WeakReference<RejectedNotificationListener<T>>(listener));
	}
	
	protected synchronized void notifyListenersOfRejectedNotification(final T notification, final RejectedNotificationReason reason) {

		final ArrayList<RejectedNotificationListener<T>> listeners = new ArrayList<RejectedNotificationListener<T>>();
		final ArrayList<Integer> expiredListenerIndices = new ArrayList<Integer>();
		
		for (int i = 0; i < this.rejectedNotificationListeners.size(); i++) {
			final RejectedNotificationListener<T> listener = this.rejectedNotificationListeners.get(i).get();
			
			if (listener != null) {
				listeners.add(listener);
			} else {
				expiredListenerIndices.add(i);
			}
		}
		
		// Handle the notifications in a separate thread in case a listener takes a long time to run
		this.rejectedNotificationExecutorService.submit(new Runnable() {

			public void run() {
				for (final RejectedNotificationListener<T> listener : listeners) {
					listener.handleRejectedNotification(notification, reason);
				}
			}
			
		});
		
		// Clear out expired listeners from right to left to avoid shifting index issues
		for (int i = expiredListenerIndices.size() - 1; i >= 0; i--) {
			this.rejectedNotificationListeners.remove(expiredListenerIndices.get(i));
		}
	}
	
	protected BlockingQueue<T> getQueue() {
		return this.queue;
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
	 * @param timeout the time after the last received data after which the connection to the feedback service should
	 * be closed
	 * @param timeoutUnit the unit of time in which the given {@code timeout} is measured
	 * 
	 * @return a list of tokens that have expired since the last connection to the feedback service
	 * 
	 * @throws InterruptedException if interrupted while waiting for a response from the feedback service
	 */
	public List<ExpiredToken> getExpiredTokens(final long timeout, final TimeUnit timeoutUnit) throws InterruptedException {
		return new FeedbackServiceClient(this).getExpiredTokens(timeout, timeoutUnit);
	}
}
