package com.relayrides.pushy.apns;

import io.netty.channel.nio.NioEventLoopGroup;

import java.security.KeyStore;
import java.util.concurrent.BlockingQueue;

public class PushManagerFactory<T extends ApnsPushNotification> {

	private final ApnsEnvironment environment;

	private final KeyStore keyStore;
	private final char[] keyStorePassword;

	private int concurrentConnectionCount = 1;

	private NioEventLoopGroup eventLoopGroup;

	private BlockingQueue<T> queue;

	public PushManagerFactory(final ApnsEnvironment environment, final KeyStore keyStore, final char[] keyStorePassword) {
		this.environment = environment;

		this.keyStore = keyStore;
		this.keyStorePassword = keyStorePassword;
	}

	public PushManagerFactory<T> setConcurrentConnectionCount(final int concurrentConnectionCount) {
		this.concurrentConnectionCount = concurrentConnectionCount;
		return this;
	}

	public PushManagerFactory<T> setEventLoopGroup(final NioEventLoopGroup eventLoopGroup) {
		this.eventLoopGroup = eventLoopGroup;
		return this;
	}

	public PushManagerFactory<T> setQueue(final BlockingQueue<T> queue) {
		this.queue = queue;
		return this;
	}

	public PushManager<T> buildPushManager() {
		return new PushManager<T>(this.environment,
				this.keyStore,
				this.keyStorePassword,
				this.concurrentConnectionCount,
				this.eventLoopGroup,
				this.queue);
	}
}
