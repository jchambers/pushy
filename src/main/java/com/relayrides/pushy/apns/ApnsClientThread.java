package com.relayrides.pushy.apns;

import com.relayrides.pushy.util.SslHandlerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class ApnsClientThread<T extends ApnsPushNotification> extends Thread {
	
	private enum ClientState {
		CONNECT,
		READY,
		RECONNECT,
		SHUTDOWN,
		EXIT
	};
	
	private final PushManager<T> pushManager;
	
	private ClientState state = null;
	
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
		
		final ApnsClientThread<T> clientThread = this;
		this.bootstrap.handler(new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				final ChannelPipeline pipeline = channel.pipeline();
				
				if (pushManager.getEnvironment().isTlsRequired()) {
					pipeline.addLast("ssl", SslHandlerFactory.getSslHandler(pushManager.getKeyStore(), pushManager.getKeyStorePassword()));
				}
				
				pipeline.addLast("decoder", new ApnsErrorDecoder());
				pipeline.addLast("encoder", new ApnsPushNotificationEncoder<T>());
				pipeline.addLast("handler", new ApnsErrorHandler<T>(pushManager, clientThread));
			}
			
		});
	}
	
	@Override
	public void start() {
		this.state = ClientState.CONNECT;
		
		super.start();
	}
	
	@Override
	public void run() {
		while (this.getClientState() != ClientState.EXIT) {
			switch (this.getClientState()) {
				case CONNECT: {
					try {
						final ChannelFuture connectFuture = this.bootstrap.connect(this.pushManager.getEnvironment().getApnsHost(), this.pushManager.getEnvironment().getApnsPort()).sync();
						
						if (connectFuture.isSuccess()) {
							this.channel = connectFuture.channel();
							
							if (this.pushManager.getEnvironment().isTlsRequired()) {
								final Future<Channel> handshakeFuture = this.channel.pipeline().get(SslHandler.class).handshakeFuture().sync();
								
								if (handshakeFuture.isSuccess()) {
									this.advanceToStateFromOriginStates(ClientState.READY, ClientState.CONNECT);
								}
							} else {
								this.advanceToStateFromOriginStates(ClientState.READY, ClientState.CONNECT);
							}
						}
					} catch (InterruptedException e) {
						continue;
					}
					
					break;
				}
				
				case READY: {
					try {
						final SendableApnsPushNotification<T> sendableNotification =
								new SendableApnsPushNotification<T>(this.pushManager.getQueue().take(), this.sequenceNumber++);
								
						this.sentNotificationBuffer.addSentNotification(sendableNotification);
						
						// TODO Don't flush on every notification if we can avoid it
						this.channel.writeAndFlush(sendableNotification).addListener(new GenericFutureListener<ChannelFuture>() {

							public void operationComplete(final ChannelFuture future) {
								if (future.cause() != null) {
									pushManager.notifyListenersOfFailedDelivery(sendableNotification.getPushNotification(), future.cause());
								}
							}});
					} catch (InterruptedException e) {
						continue;
					}
					
					break;
				}
				
				case RECONNECT: {
					if (this.channel != null && this.channel.isOpen()) {
						this.channel.close();
					}
					
					try {
						this.channel.closeFuture().sync();
						this.advanceToStateFromOriginStates(ClientState.CONNECT, ClientState.RECONNECT);
					} catch (InterruptedException e) {
						continue;
					}
					
					break;
				}
				
				case SHUTDOWN: {
					try {
						if (this.channel != null && this.channel.isOpen()) {
							this.channel.close().sync();
						}
						
						this.bootstrap.group().shutdownGracefully().sync();
					} catch (InterruptedException e) {
						continue;
					}
					
					this.advanceToStateFromOriginStates(ClientState.EXIT, ClientState.SHUTDOWN);
					
					break;
				}
				
				case EXIT: {
					// Do nothing
					break;
				}
				
				default: {
					throw new IllegalArgumentException(String.format("Unexpected state: %s", this.getState()));
				}
			}
		}
	}
	
	protected void reconnect() {
		// We don't want to try to reconnect if we're already connecting or on our way out
		this.advanceToStateFromOriginStates(ClientState.RECONNECT, ClientState.READY);
		this.interrupt();
	}
	
	public void shutdown() {
		// Don't re-shut-down if we're already on our way out
		this.advanceToStateFromOriginStates(ClientState.SHUTDOWN, ClientState.CONNECT, ClientState.READY, ClientState.RECONNECT);
		this.interrupt();
	}
	
	private ClientState getClientState() {
		synchronized (this.state) {
			return this.state;
		}
	}
	
	/**
	 * Sets the current state if and only if the current state is in one of the allowed origin states.
	 */
	private void advanceToStateFromOriginStates(final ClientState destinationState, final ClientState... allowableOriginStates) {
		synchronized (this.state) {
			for (final ClientState originState : allowableOriginStates) {
				if (this.state == originState) {
					this.state = destinationState;
					break;
				}
			}
		}
	}
	
	protected SentNotificationBuffer<T> getSentNotificationBuffer() {
		return this.sentNotificationBuffer;
	}
}
