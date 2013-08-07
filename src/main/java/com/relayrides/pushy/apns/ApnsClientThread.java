package com.relayrides.pushy.apns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.relayrides.pushy.util.SslHandlerFactory;

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
	
	private class SentNotificationBuffer<E extends ApnsPushNotification> {
		
		private final LinkedList<SendableApnsPushNotification<E>> buffer;
		private final int capacity;
		
		public SentNotificationBuffer(final int capacity) {
			this.buffer = new LinkedList<SendableApnsPushNotification<E>>();
			this.capacity = capacity;
		}
		
		public synchronized void addSentNotification(SendableApnsPushNotification<E> notification) {
			this.buffer.addLast(notification);
			
			while (this.buffer.size() > this.capacity) {
				this.buffer.removeFirst();
			}
		}
		
		public synchronized E getFailedNotificationAndClearBuffer(final int failedNotificationId, final PushManager<E> pushManager) {
			while (this.buffer.getFirst().isSequentiallyBefore(failedNotificationId)) {
				this.buffer.removeFirst();
			}
			
			final E failedNotification = this.buffer.getFirst().getNotificationId() == failedNotificationId ?
					this.buffer.removeFirst().getPushNotification() : null;
			
			{
				final ArrayList<E> unsentNotifications = new ArrayList<E>(this.buffer.size());
				
				for (final SendableApnsPushNotification<E> sentNotification : this.buffer) {
					unsentNotifications.add(sentNotification.getPushNotification());
				}
				
				pushManager.enqueueAllNotifications(unsentNotifications);
			}
			
			this.buffer.clear();
			
			return failedNotification;
		}
	}
	
	private class ApnsErrorDecoder extends ByteToMessageDecoder {

		// Per Apple's docs, APNS errors will have a one-byte "command", a one-byte status, and a 4-byte notification ID
		private static final int EXPECTED_BYTES = 6;
		private static final byte EXPECTED_COMMAND = 8;
		
		@Override
		protected void decode(final ChannelHandlerContext context, final ByteBuf in, final List<Object> out) {
			if (in.readableBytes() >= EXPECTED_BYTES) {
				final byte command = in.readByte();
				final byte code = in.readByte();
				
				final int notificationId = in.readInt();
				
				if (command != EXPECTED_COMMAND) {
					throw new IllegalArgumentException(String.format("Unexpected command: %d", command));
				}
				
				final ApnsErrorCode errorCode = ApnsErrorCode.getByCode(code);
				
				out.add(new ApnsException(notificationId, errorCode));
			}
		}
	}
	
	private class ApnsPushNotificationEncoder extends MessageToByteEncoder<SendableApnsPushNotification<T>> {
	
		private static final byte ENHANCED_PUSH_NOTIFICATION_COMMAND = 1;
		private static final int EXPIRE_IMMEDIATELY = 0;
		
		private final Charset utf8 = Charset.forName("UTF-8");
		
		@Override
		protected void encode(final ChannelHandlerContext context, final SendableApnsPushNotification<T> sendablePushNotification, final ByteBuf out) throws Exception {
			out.writeByte(ENHANCED_PUSH_NOTIFICATION_COMMAND);
			out.writeInt(sendablePushNotification.getNotificationId());
			
			if (sendablePushNotification.getPushNotification().getDeliveryInvalidationTime() != null) {
				out.writeInt(this.getTimestampInSeconds(sendablePushNotification.getPushNotification().getDeliveryInvalidationTime()));
			} else {
				out.writeInt(EXPIRE_IMMEDIATELY);
			}
			
			out.writeShort(sendablePushNotification.getPushNotification().getToken().length);
			out.writeBytes(sendablePushNotification.getPushNotification().getToken());
			
			final byte[] payloadBytes = sendablePushNotification.getPushNotification().getPayload().getBytes(utf8);
			
			out.writeShort(payloadBytes.length);
			out.writeBytes(payloadBytes);
		}
		
		private int getTimestampInSeconds(final Date date) {
			return (int)(date.getTime() / 1000);
		}
	}

	private class ApnsErrorHandler extends SimpleChannelInboundHandler<ApnsException> {

		private final PushManager<T> pushManager;
		private final ApnsClientThread<T> clientThread;
		
		private final Logger log = LoggerFactory.getLogger(ApnsErrorHandler.class);
		
		public ApnsErrorHandler(final PushManager<T> pushManager, final ApnsClientThread<T> clientThread) {
			this.pushManager = pushManager;
			this.clientThread = clientThread;
		}
		
		@Override
		protected void channelRead0(final ChannelHandlerContext context, final ApnsException e) throws Exception {
			this.clientThread.reconnect();
			
			final T failedNotification =
					this.clientThread.getSentNotificationBuffer().getFailedNotificationAndClearBuffer(e.getNotificationId(), pushManager);
			
			if (e.getErrorCode() != ApnsErrorCode.SHUTDOWN) {
				this.pushManager.notifyListenersOfFailedDelivery(failedNotification, e);
			}
		}
		
		@Override
		public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
			this.clientThread.reconnect();
			
			log.debug("Caught an exception; reconnecting.", cause);
		}
	}
	
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
				pipeline.addLast("encoder", new ApnsPushNotificationEncoder());
				pipeline.addLast("handler", new ApnsErrorHandler(pushManager, clientThread));
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
