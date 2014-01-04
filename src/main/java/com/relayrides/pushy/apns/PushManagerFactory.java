package com.relayrides.pushy.apns;

import io.netty.channel.nio.NioEventLoopGroup;

import java.security.KeyStore;
import java.util.concurrent.BlockingQueue;

/**
 * A {@code PushManagerFactory} is used to configure and construct a new {@link PushManager}.
 * 
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public class PushManagerFactory<T extends ApnsPushNotification> {

	private final ApnsEnvironment environment;

	private final KeyStore keyStore;
	private final char[] keyStorePassword;

	private int concurrentConnectionCount = 1;

	private NioEventLoopGroup eventLoopGroup;

	private BlockingQueue<T> queue;

	/**
	 * Constructs a new factory that will construct {@link PushManager}s that operate in the given environment with the
	 * given credentials.
	 * 
	 * @param environment the environment in which constructed {@code PushManager}s will operate
	 * @param keyStore A {@code KeyStore} containing the client key to present during a TLS handshake; may be
	 * {@code null} if the environment does not require TLS. The {@code KeyStore} should be loaded before being used
	 * here.
	 * @param keyStorePassword a password to unlock the given {@code KeyStore}; may be {@code null}
	 */
	public PushManagerFactory(final ApnsEnvironment environment, final KeyStore keyStore, final char[] keyStorePassword) {
		this.environment = environment;

		this.keyStore = keyStore;
		this.keyStorePassword = keyStorePassword;
	}

	/**
	 * <p>Sets the number of concurrent connections constructed {@code PushManagers} should maintain to the APNs
	 * gateway. By default, constructed {@code PushManagers} will maintain a single connection to the gateway.</p>
	 * 
	 * @param concurrentConnectionCount the number of parallel connections to maintain
	 * 
	 * @return a reference to this factory for ease of chaining configuration calls
	 */
	public PushManagerFactory<T> setConcurrentConnectionCount(final int concurrentConnectionCount) {
		this.concurrentConnectionCount = concurrentConnectionCount;
		return this;
	}

	/**
	 * <p>Sets a custom event loop group to be used by constructed {@code PushMangers}. If {@code null}, constructed
	 * {@code PushManagers} will be create and maintain their own event loop groups. If a non-{@code null} event loop
	 * group is provided, callers <strong>must</strong> shut down the event loop group after shutting down all
	 * {@code PushManager} instances that use that event loop group.</p>
	 * 
	 * <p>By default, constructed {@code PushManagers} will construct and maintain their own event loop groups.</p>
	 * 
	 * @param eventLoopGroup the event loop group constructed {@code PushManagers} should use for their connections to
	 * the APNs gateway and feedback service; if {@code null}, a new event loop group will be created and will be shut
	 * down automatically when the push manager is shut down. If not {@code null}, the caller <strong>must</strong> shut
	 * down the event loop group after shutting down all push managers that use the group
	 * 
	 * @return a reference to this factory for ease of chaining configuration calls
	 */
	public PushManagerFactory<T> setEventLoopGroup(final NioEventLoopGroup eventLoopGroup) {
		this.eventLoopGroup = eventLoopGroup;
		return this;
	}

	/**
	 * <p>Sets the queue to be used to pass new notifications to constructed {@code PushManagers}. If {@code null} (the
	 * default), constructed push managers will construct their own queues.</p>
	 * 
	 * @param queue the queue to be used to pass new notifications to constructed push managers
	 * @return
	 */
	public PushManagerFactory<T> setQueue(final BlockingQueue<T> queue) {
		this.queue = queue;
		return this;
	}

	/**
	 * <p>Constructs a new {@link PushManager} with the settings provided to this factory. The returned push manager
	 * will not be started automatically.</p>
	 * 
	 * @return a new, configured {@code PushManager}
	 */
	public PushManager<T> buildPushManager() {
		return new PushManager<T>(this.environment,
				this.keyStore,
				this.keyStorePassword,
				this.concurrentConnectionCount,
				this.eventLoopGroup,
				this.queue);
	}
}
