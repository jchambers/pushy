package com.relayrides.pushy.apns;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import com.relayrides.pushy.apns.ApnsErrorCode;
import com.relayrides.pushy.util.SimpleApnsPushNotification;

public class MockApnsServer {
	
	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	
	private final int port;
	private final int maxPayloadSize;
	
	private final Vector<SimpleApnsPushNotification> receivedNotifications;
	
	private int failWithErrorCount = 0;
	private ApnsErrorCode errorCode;
	
	public MockApnsServer(final int port, final int maxPayloadSize) {
		this.port = port;
		this.maxPayloadSize = maxPayloadSize;
		
		this.receivedNotifications = new Vector<SimpleApnsPushNotification>();
	}
	
	public void start() throws InterruptedException {
		this.bossGroup = new NioEventLoopGroup();
		this.workerGroup = new NioEventLoopGroup();
		
		final ServerBootstrap bootstrap = new ServerBootstrap();
		
		bootstrap.group(bossGroup, workerGroup);
		bootstrap.channel(NioServerSocketChannel.class);
		
		final MockApnsServer server = this;
		
		bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				channel.pipeline().addLast("decoder", new ApnsPushNotificationDecoder(maxPayloadSize));
				channel.pipeline().addLast("encoder", new ApnsErrorEncoder());
				channel.pipeline().addLast("handler", new MockApnsServerHandler(server));
			}
			
		});
		
		bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
		
		bootstrap.bind(this.port).sync();
	}
	
	public void shutdown() throws InterruptedException {
		this.workerGroup.shutdownGracefully();
		this.bossGroup.shutdownGracefully();
	}
	
	public void failWithErrorAfterNotifications(final ApnsErrorCode errorCode, final int notificationCount) {
		this.failWithErrorCount = notificationCount;
		this.errorCode = errorCode;
	}
	
	protected RejectedNotificationException handleReceivedNotification(final ReceivedApnsPushNotification<SimpleApnsPushNotification> receivedNotification) {
		synchronized (this.receivedNotifications) {
			this.receivedNotifications.add(receivedNotification.getPushNotification());
			
			if (this.receivedNotifications.size() == this.failWithErrorCount) {
				return new RejectedNotificationException(receivedNotification.getNotificationId(), this.errorCode);
			} else {
				return null;
			}
		}
	}
	
	public List<SimpleApnsPushNotification> getReceivedNotifications() {
		return new ArrayList<SimpleApnsPushNotification>(this.receivedNotifications);
	}
}
