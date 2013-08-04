package com.relayrides.pushy.apns;

import com.relayrides.pushy.ApnsPushNotification;
import com.relayrides.pushy.PushManager;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class ApnsClientThread<T extends ApnsPushNotification> extends Thread {
	
	private enum State {
		CONNECT,
		READY,
		WAIT,
		RECONNECT,
		SHUTDOWN,
		EXIT
	};
	
	private final PushManager<T> pushManager;
	
	private volatile State state = null;
	
	private final Bootstrap bootstrap;
	
	private Channel channel = null;
	private int sequenceNumber = 0;
	
	private final SentNotificationBuffer<T> sentNotificationBuffer;
	
	private static final int SENT_NOTIFICATION_BUFFER_SIZE = 2048;
	
	public ApnsClientThread(final PushManager<T> pushManager) {
		super("ApnsClientThread");
		
		this.pushManager = pushManager;
		
		this.sentNotificationBuffer = new SentNotificationBuffer<T>(SENT_NOTIFICATION_BUFFER_SIZE);
		
		this.bootstrap = new Bootstrap();
		this.bootstrap.group(new NioEventLoopGroup());
		this.bootstrap.channel(NioSocketChannel.class);
		this.bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
		
		final ApnsClientInitializer<T> initializer = new ApnsClientInitializer<T>(this.pushManager, this);
		this.bootstrap.handler(initializer);
	}
	
	@Override
	public void run() {
		this.state = State.CONNECT;
		
		while (this.state != State.EXIT) {
			switch (this.state) {
				case CONNECT: {
					try {
						this.channel = this.bootstrap.connect(this.pushManager.getEnvironment().getHost(), this.pushManager.getEnvironment().getPort()).sync().channel();
						
						this.state = State.READY;
					} catch (InterruptedException e) {
						continue;
					}
					
					break;
				}
				
				case READY: {
					try {
						final SendableApnsPushNotification<T> sendableNotification =
								new SendableApnsPushNotification<T>(this.pushManager.getQueue().take(), this.sequenceNumber++);
								
						this.channel.write(sendableNotification);
						this.sentNotificationBuffer.addSentNotification(sendableNotification);
					} catch (InterruptedException e) {
						continue;
					}
					
					break;
				}
				
				case WAIT: {
					Thread.yield();
					break;
				}
				
				case RECONNECT: {
					try {
						this.channel.closeFuture().sync();
						this.state = State.CONNECT;
					} catch (InterruptedException e) {
						continue;
					}
					
					break;
				}
				
				case SHUTDOWN: {
					try {
						if (this.channel.isOpen()) {
							this.channel.close().sync();
						}
						
						this.bootstrap.group().shutdownGracefully().sync();
					} catch (InterruptedException e) {
						continue;
					}
					
					this.state = State.EXIT;
					break;
				}
				
				case EXIT:
				default: {
					// Do nothing
				}
			}
		}
	}
	
	protected void beginErrorHandling() {
		this.state = State.WAIT;
		this.interrupt();
	}
	
	protected void endErrorHandling() {
		this.state = State.RECONNECT;
	}
	
	public void shutdown() {
		this.state = State.SHUTDOWN;
		this.interrupt();
	}
	
	protected SentNotificationBuffer<T> getSentNotificationBuffer() {
		return this.sentNotificationBuffer;
	}
}
