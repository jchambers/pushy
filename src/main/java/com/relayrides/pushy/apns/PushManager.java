package com.relayrides.pushy.apns;

import java.lang.ref.WeakReference;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * <p>A {@code PushManager} is the main public-facing point of interaction with APNs. {@code PushManager}s manage the
 * queue of outbound push notifications and manage connections to the various APNs servers.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 * @param <T>
 */
public class PushManager<T extends ApnsPushNotification> {
	private final BlockingQueue<T> queue;
	
	private final ApnsEnvironment environment;
	private final KeyStore keyStore;
	private final char[] keyStorePassword;
	
	private final ArrayList<ApnsClientThread<T>> clientThreads;
	private final FeedbackServiceClient feedbackClient;
	
	private final ArrayList<WeakReference<RejectedNotificationListener<T>>> failedDeliveryListeners;
	
	/**
	 * Constructs a new {@code PushManager} that operates in the given environment with the given credentials and a
	 * single connection to APNs.
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
	 * <p>Constructs a new {@code PushManager} that operates in the given environment with the given credentials and the
	 * given number of parallel connections to APNs. See
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
		
		if (environment.isTlsRequired() && keyStore == null) {
			throw new IllegalArgumentException("Must include a non-null KeyStore for environments that require TLS.");
		}
		
		this.queue = new LinkedBlockingQueue<T>();
		this.failedDeliveryListeners = new ArrayList<WeakReference<RejectedNotificationListener<T>>>();
		
		this.environment = environment;
		
		this.keyStore = keyStore;
		this.keyStorePassword = keyStorePassword;
		
		this.clientThreads = new ArrayList<ApnsClientThread<T>>(concurrentConnections);
		
		for (int i = 0; i < concurrentConnections; i++) {
			this.clientThreads.add(new ApnsClientThread<T>(this));
		}
		
		this.feedbackClient = new FeedbackServiceClient(this);
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
		for (final ApnsClientThread<T> clientThread : this.clientThreads) {
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
	 * Disconnects from the APNs and gracefully shuts down all worker threads.
	 * 
	 * @return a list of notifications not sent before the {@code PushManager} shut down
	 * 
	 * @throws InterruptedException if interrupted while waiting for worker threads to exit cleanly
	 */
	public synchronized List<T> shutdown() throws InterruptedException {
		for (final ApnsClientThread<T> clientThread : this.clientThreads) {
			clientThread.shutdown();
		}
		
		for (final ApnsClientThread<T> clientThread : this.clientThreads) {
			clientThread.join();
		}
		
		this.feedbackClient.destroy();
		
		return new ArrayList<T>(this.queue);
	}
	
	/**
	 * <p>Registers a listener for notifications rejected by APNs for specific reasons. Note that listeners are stored
	 * as weak references, so callers should maintain a reference to listeners to prevent them from being garbage
	 * collected.</p>
	 * 
	 * @param listener the listener to register
	 */
	public void registerRejectedNotificationListener(final RejectedNotificationListener<T> listener) {
		this.failedDeliveryListeners.add(new WeakReference<RejectedNotificationListener<T>>(listener));
	}
	
	protected void notifyListenersOfRejectedNotification(final T notification, final RejectedNotificationException cause) {
		for (final WeakReference<RejectedNotificationListener<T>> listenerReference : this.failedDeliveryListeners) {
			final RejectedNotificationListener<T> listener = listenerReference.get();
			
			// TODO Clear out entries with expired references
			if (listener != null) {
				listener.handleRejectedNotification(notification, cause);
			}
		}
	}
	
	protected BlockingQueue<T> getQueue() {
		return this.queue;
	}
	
	/**
	 * <p>Queries the APNs feedback service for expired tokens. Be warned that this is a <strong>destructive
	 * operation</strong>. According to Apple's documentation:</p>
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
		return this.feedbackClient.getExpiredTokens();
	}
}
