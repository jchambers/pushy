package com.relayrides.pushy.apns;

import java.security.KeyStore;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class ApnsClient {
	
	private final String host;
	private final int port;
	
	private final KeyStore keyStore;
	private final char[] keyStorePassword;
	
	public ApnsClient(final String host, final int port, final KeyStore keyStore, final char[] keyStorePassword) {
		this.host = host;
		this.port = port;
		
		this.keyStore = keyStore;
		this.keyStorePassword = keyStorePassword;
	}
	
	public void start() throws InterruptedException {
		final Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(new NioEventLoopGroup());
		bootstrap.channel(NioSocketChannel.class);
		bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
		
		final ApnsClientInitializer initializer = new ApnsClientInitializer(keyStore, keyStorePassword);
		bootstrap.handler(initializer);
		
		bootstrap.connect(this.host, this.port).sync();
		
		// TODO Start processing messages
	}
	
	public void shutdown() {
		// TODO
	}
}
