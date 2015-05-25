package com.relayrides.pushy.apns;

import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.nio.NioEventLoopGroup;

import javax.net.ssl.SSLContext;

public class DefaultFeedbackServiceConnectionFactory implements FeedbackServiceConnectionFactory {

	private final ApnsEnvironment environment;
	private final SSLContext sslContext;
	private final NioEventLoopGroup eventLoopGroup;
	private final String namePrefix;

	private final AtomicInteger connectionCounter = new AtomicInteger(0);

	public DefaultFeedbackServiceConnectionFactory(final ApnsEnvironment environment, final SSLContext sslContext, final NioEventLoopGroup eventLoopGroup, final String namePrefix) {
		this.environment = environment;
		this.sslContext = sslContext;
		this.eventLoopGroup = eventLoopGroup;
		this.namePrefix = namePrefix;
	}

	@Override
	public FeedbackServiceConnection createFeedbackConnection() {
		return new FeedbackServiceConnection(this.environment, this.sslContext, this.eventLoopGroup,
				String.format("%s-%d", this.namePrefix, this.connectionCounter.getAndIncrement()));
	}
}
