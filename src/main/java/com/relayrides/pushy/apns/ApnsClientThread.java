package com.relayrides.pushy.apns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
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
import java.util.Date;
import java.util.List;

/**
 * <p>A worker thread that connects to an APNs server and transmits notifications from a {@code PushManager}'s
 * queue.</p>
 * 
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 * @param <T>
 */
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
	
	private volatile ChannelFuture lastWriteFuture;
	
	private final SentNotificationBuffer<T> sentNotificationBuffer;
	private static final int SENT_NOTIFICATION_BUFFER_SIZE = 2048;
	
	private class RejectedNotificationDecoder extends ByteToMessageDecoder {

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
				
				final RejectedNotificationReason errorCode = RejectedNotificationReason.getByErrorCode(code);
				
				out.add(new RejectedNotificationException(notificationId, errorCode));
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
			out.writeInt(sendablePushNotification.getSequenceNumber());
			
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

	private class ApnsErrorHandler extends SimpleChannelInboundHandler<RejectedNotificationException> {

		private final ApnsClientThread<T> clientThread;
		
		public ApnsErrorHandler(final ApnsClientThread<T> clientThread) {
			this.clientThread = clientThread;
		}
		
		@Override
		protected void channelRead0(final ChannelHandlerContext context, final RejectedNotificationException e) throws Exception {
			this.clientThread.handleRejectedNotification(e);
		}
		
		@Override
		public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
			// Assume this is a temporary IO problem and reconnect. Some writes will fail, but will be re-enqueued.
			this.clientThread.reconnect();
		}
	}
	
	/**
	 * Constructs a new APNs client thread. The thread connects to the APNs gateway in the given {@code PushManager}'s
	 * environment and reads notifications from the {@code PushManager}'s queue.
	 * 
	 * @param pushManager the {@code PushManager} from which this client thread should read environment settings and
	 * notifications
	 */
	public ApnsClientThread(final PushManager<T> pushManager) {
		super("ApnsClientThread");
		
		this.state = ClientState.CONNECT;
		
		this.pushManager = pushManager;
		
		this.sentNotificationBuffer = new SentNotificationBuffer<T>(SENT_NOTIFICATION_BUFFER_SIZE);
		
		this.bootstrap = new Bootstrap();
		this.bootstrap.group(new NioEventLoopGroup());
		this.bootstrap.channel(NioSocketChannel.class);
		this.bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
		
		final ApnsClientThread<T> clientThread = this;
		this.bootstrap.handler(new SslCapableChannelInitializer() {
			
			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				final ChannelPipeline pipeline = channel.pipeline();
				
				if (pushManager.getEnvironment().isTlsRequired()) {
					pipeline.addLast("ssl", this.getSslHandler(pushManager.getKeyStore(), pushManager.getKeyStorePassword()));
				}
				
				pipeline.addLast("decoder", new RejectedNotificationDecoder());
				pipeline.addLast("encoder", new ApnsPushNotificationEncoder());
				pipeline.addLast("handler", new ApnsErrorHandler(clientThread));
			}
		});
	}
	
	/**
	 * Continually polls this thread's {@code PushManager}'s queue for new messages and sends them to the APNs
	 * gateway. Automatically reconnects as needed.
	 */
	@Override
	public void run() {
		while (this.getClientState() != ClientState.EXIT) {
			switch (this.getClientState()) {
				case CONNECT: {
					try {
						this.connect();
					} catch (InterruptedException e) {
						continue;
					}
					
					break;
				}
				
				case READY: {
					try {
						final T notification = this.pushManager.getQueue().take();
						
						// TODO Don't flush on every notification if we can avoid it
						final SendableApnsPushNotification<T> sendableNotification =
								new SendableApnsPushNotification<T>(notification, this.sequenceNumber++);
						
						this.sentNotificationBuffer.addSentNotification(sendableNotification);
						
						this.lastWriteFuture = this.channel.writeAndFlush(sendableNotification);
						this.lastWriteFuture.addListener(new GenericFutureListener<ChannelFuture>() {

							public void operationComplete(final ChannelFuture future) {
								if (future.cause() != null) {
									reconnect();
									
									// Delivery failed for some IO-related reason; re-enqueue for another attempt, but
									// only if the notification is in the sent notification buffer (i.e. if it hasn't
									// been re-enqueued for another reason).
									final T failedNotification = sentNotificationBuffer.getAndRemoveNotificationWithSequenceNumber(
											sendableNotification.getSequenceNumber());
									
									if (failedNotification != null) {
										pushManager.enqueuePushNotification(failedNotification);
									}
								}
							}
						});
						
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
	
	protected void connect() throws InterruptedException {
		final ChannelFuture connectFuture =
				this.bootstrap.connect(this.pushManager.getEnvironment().getApnsGatewayHost(), this.pushManager.getEnvironment().getApnsGatewayPort()).sync();
		
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
	}
	
	protected void handleRejectedNotification(final RejectedNotificationException e) {
		this.reconnect();
		
		// SHUTDOWN errors from Apple are harmless; nothing bad happened with the delivered notification, so
		// we don't want to notify listeners of the error (but we still do need to reconnect).
		if (e.getReason() != RejectedNotificationReason.SHUTDOWN) {
			this.pushManager.notifyListenersOfRejectedNotification(
					this.getSentNotificationBuffer().getAndRemoveNotificationWithSequenceNumber(e.getSequenceNumberId()), e);
		}
		
		this.pushManager.enqueueAllNotifications(
				this.sentNotificationBuffer.getAndRemoveAllNotificationsAfterSequenceNumber(e.getSequenceNumberId()));
	}
	
	protected void reconnect() {
		if (this.advanceToStateFromOriginStates(ClientState.RECONNECT, ClientState.READY)) {
			this.interrupt();
		}
	}
	
	/**
	 * Gracefully and asynchronously shuts down this client thread.
	 */
	public void shutdown() {
		// Don't re-shut-down if we're already on our way out
		if (this.advanceToStateFromOriginStates(ClientState.SHUTDOWN, ClientState.CONNECT, ClientState.READY, ClientState.RECONNECT)) {
			this.interrupt();
		}
	}
	
	private ClientState getClientState() {
		synchronized (this.state) {
			return this.state;
		}
	}
	
	/**
	 * Sets the current state if and only if the current state is in one of the allowed origin states.
	 */
	private boolean advanceToStateFromOriginStates(final ClientState destinationState, final ClientState... allowableOriginStates) {
		synchronized (this.state) {
			for (final ClientState originState : allowableOriginStates) {
				if (this.state == originState) {
					this.state = destinationState;
					return true;
				}
			}
			
			return false;
		}
	}
	
	protected SentNotificationBuffer<T> getSentNotificationBuffer() {
		return this.sentNotificationBuffer;
	}
	
	protected ChannelFuture getLastWriteFuture() {
		return this.lastWriteFuture;
	}
}
