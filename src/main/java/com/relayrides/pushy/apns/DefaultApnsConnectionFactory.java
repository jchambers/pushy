package com.relayrides.pushy.apns;

import io.netty.channel.nio.NioEventLoopGroup;

import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

public class DefaultApnsConnectionFactory<T extends ApnsPushNotification> implements ApnsConnectionFactory<T> {

	private final ApnsEnvironment environment;
	private final SSLContext sslContext;
	private final NioEventLoopGroup eventLoopGroup;
	private final String namePrefix;

	private final AtomicInteger connectionCounter = new AtomicInteger(0);

	public DefaultApnsConnectionFactory(final ApnsEnvironment environment, final SSLContext sslContext,
			final NioEventLoopGroup eventLoopGroup, final String namePrefix) {

		this.environment = environment;
		this.sslContext = sslContext;
		this.eventLoopGroup = eventLoopGroup;
		this.namePrefix = namePrefix;
	}

	@Override
	public ApnsConnection<T> createApnsConnection() {
		return new ApnsConnection<T>(this.environment, this.sslContext, this.eventLoopGroup,
				String.format("%s-%d", this.namePrefix, this.connectionCounter.getAndIncrement()),
				ApnsConnection.DEFAULT_SENT_NOTIFICATION_BUFFER_CAPACITY);
	}
}
