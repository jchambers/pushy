package com.relayrides.pushy.apns;

import java.security.KeyStore;
import java.util.concurrent.BlockingQueue;

import com.relayrides.pushy.ApnsPushNotification;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class ApnsClient {
	
	private final ApnsEnvironment environment;
	
	private final KeyStore keyStore;
	private final char[] keyStorePassword;
	
	private final BlockingQueue<? extends ApnsPushNotification> queue;
	
	private volatile boolean shouldContinue;
	
	public ApnsClient(final ApnsEnvironment environment, final KeyStore keyStore, final char[] keyStorePassword, final BlockingQueue<? extends ApnsPushNotification> queue) {
		this.environment = environment;
		
		this.keyStore = keyStore;
		this.keyStorePassword = keyStorePassword;
		
		this.queue = queue;
	}
	
	public void start() throws InterruptedException {
		final Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(new NioEventLoopGroup());
		bootstrap.channel(NioSocketChannel.class);
		bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
		
		final ApnsClientInitializer initializer = new ApnsClientInitializer(keyStore, keyStorePassword);
		bootstrap.handler(initializer);
		
		final Channel channel = bootstrap.connect(this.environment.getHost(), this.environment.getPort()).sync().channel();
		
		while (this.shouldContinue) {
			// TODO Actually send the message
			this.queue.take();
		}
	}
	
	public void shutdown() {
		// TODO
	}
}
