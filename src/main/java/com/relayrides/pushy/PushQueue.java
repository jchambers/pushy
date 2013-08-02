package com.relayrides.pushy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PushQueue {
	private final BlockingQueue<PushNotification> queue;
	
	private final String host;
	private final int port;
	
	private ChannelFuture channelFuture;
	
	public PushQueue(final String host, final int port) {
		this.queue = new LinkedBlockingQueue<PushNotification>();
		
		this.host = host;
		this.port = port;
	}
	
	public void start() throws InterruptedException {
		final EventLoopGroup workerGroup = new NioEventLoopGroup();
		
		final Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(workerGroup);
		
		this.channelFuture = bootstrap.connect().sync();
	}
	
	public void shutdown() {
		
	}
}
