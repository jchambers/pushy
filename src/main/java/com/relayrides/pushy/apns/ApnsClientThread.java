/* Copyright (c) 2013 RelayRides
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

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
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>A worker thread that connects to an APNs server and transmits notifications from a {@code PushManager}'s
 * queue.</p>
 * 
 * <p>Generally, users of Pushy should <em>not</em> instantiate an {@code ApnsClientThread} directly, but should
 * instead construct a {@link PushManager}, which will manage the lifecycle of one or more client threads.</p>
 * 
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
class ApnsClientThread<T extends ApnsPushNotification> extends Thread {
	
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
	
	private static final long POLL_TIMEOUT = 50;
	private static final int BATCH_SIZE = 32;
	private int writesSinceLastFlush = 0;
	
	private static AtomicInteger threadCounter = new AtomicInteger(0);
	
	private final Logger log = LoggerFactory.getLogger(ApnsClientThread.class);
	
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
					log.error(String.format("Unexpected command: %d", command));
				}
				
				final RejectedNotificationReason errorCode = RejectedNotificationReason.getByErrorCode(code);
				
				out.add(new RejectedNotification(notificationId, errorCode));
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

	private class ApnsErrorHandler extends SimpleChannelInboundHandler<RejectedNotification> {

		private final ApnsClientThread<T> clientThread;
		
		public ApnsErrorHandler(final ApnsClientThread<T> clientThread) {
			this.clientThread = clientThread;
		}
		
		@Override
		protected void channelRead0(final ChannelHandlerContext context, final RejectedNotification rejectedNotification) throws Exception {
			this.clientThread.handleRejectedNotification(rejectedNotification);
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
		super(String.format("ApnsClientThread-%d", ApnsClientThread.threadCounter.incrementAndGet()));
		
		this.state = ClientState.CONNECT;
		
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
					pipeline.addLast("ssl", SslHandlerUtil.createSslHandler(pushManager.getKeyStore(), pushManager.getKeyStorePassword()));
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
						final T notification = this.pushManager.getQueue().poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
						
						if (notification != null) {
							final SendableApnsPushNotification<T> sendableNotification =
									new SendableApnsPushNotification<T>(notification, this.sequenceNumber++);
							
							final String threadName = this.getName();
							
							if (log.isTraceEnabled()) {
								log.trace(String.format("%s sending %s", threadName, sendableNotification));
							}
							
							this.sentNotificationBuffer.addSentNotification(sendableNotification);
							
							this.channel.write(sendableNotification).addListener(new GenericFutureListener<ChannelFuture>() {

								public void operationComplete(final ChannelFuture future) {
									if (future.cause() != null) {
										if (log.isTraceEnabled()) {
											log.trace(String.format("%s failed to write notification %s",
													threadName, sendableNotification), future.cause());
										}

										reconnect();
										
										// Delivery failed for some IO-related reason; re-enqueue for another attempt, but
										// only if the notification is in the sent notification buffer (i.e. if it hasn't
										// been re-enqueued for another reason).
										final T failedNotification = sentNotificationBuffer.getAndRemoveNotificationWithSequenceNumber(
												sendableNotification.getSequenceNumber());
										
										if (failedNotification != null) {
											pushManager.enqueuePushNotification(failedNotification);
										}
									} else {
										if (log.isTraceEnabled()) {
											log.trace(String.format("%s successfully wrote notification %d",
													threadName, sendableNotification.getSequenceNumber()));
										}
									}
								}
							});
							
							if (++this.writesSinceLastFlush >= ApnsClientThread.BATCH_SIZE) {
								this.channel.flush();
								this.writesSinceLastFlush = 0;
							}
						} else {
							if (this.writesSinceLastFlush > 0) {
								this.channel.flush();
								this.writesSinceLastFlush = 0;
							}
						}
						
					} catch (InterruptedException e) {
						this.channel.flush();
					}
					
					break;
				}
				
				case RECONNECT: {
					if (this.channel != null && this.channel.isOpen()) {
						this.channel.close();
					}
					
					try {
						log.debug(String.format("%s waiting for connection to close.", this.getName()));
						
						this.channel.closeFuture().await();
						
						if (this.channel.closeFuture().cause() != null) {
							log.warn(String.format("%s failed to cleanly close its connection.", this.getName()),
									this.channel.closeFuture().cause());
						}
						
						this.advanceToStateFromOriginStates(ClientState.CONNECT, ClientState.RECONNECT);
					} catch (InterruptedException e) {
						log.warn(String.format("%s interrupted while waiting for connection to close.", this.getName()));
						continue;
					}
					
					break;
				}
				
				case SHUTDOWN: {
					try {
						if (this.channel != null && this.channel.isOpen()) {
							log.debug(String.format("%s waiting for connection to close.", this.getName()));
							this.channel.close().await();
							
							if (this.channel.closeFuture().cause() != null) {
								log.warn(String.format("%s failed to cleanly close its connection.", this.getName()),
										this.channel.closeFuture().cause());
							}
						}
						
						log.debug(String.format("%s shutting down worker group.", this.getName()));
						this.bootstrap.group().shutdownGracefully().await();
						
						if (this.channel.closeFuture().cause() != null) {
							log.warn(String.format("%s failed to cleanly close its connection.", this.getName()));
						}
					} catch (InterruptedException e) {
						log.warn(String.format("%s interrupted while waiting for connection to close.", this.getName()));
						continue;
					}
					
					this.advanceToStateFromOriginStates(ClientState.EXIT, ClientState.SHUTDOWN);
					
					break;
				}
				
				case EXIT: {
					// Do nothing; we'll exit on the next iteration
					break;
				}
				
				default: {
					throw new IllegalArgumentException(String.format("Unexpected state: %s", this.getState()));
				}
			}
		}
	}
	
	protected void connect() throws InterruptedException {
		log.debug(String.format("%s beginning connection process.", this.getName()));
		
		final ChannelFuture connectFuture =
				this.bootstrap.connect(
						this.pushManager.getEnvironment().getApnsGatewayHost(),
						this.pushManager.getEnvironment().getApnsGatewayPort()).await();
		
		if (connectFuture.isSuccess()) {
			log.debug(String.format("%s connected.", this.getName()));
			
			this.channel = connectFuture.channel();
			
			if (this.pushManager.getEnvironment().isTlsRequired()) {
				log.debug(String.format("%s waiting for TLS handshake.", this.getName()));
				
				final Future<Channel> handshakeFuture = this.channel.pipeline().get(SslHandler.class).handshakeFuture().await();
				
				if (handshakeFuture.isSuccess()) {
					log.debug(String.format("%s successfully completed TLS handshake.", this.getName()));
					
					this.advanceToStateFromOriginStates(ClientState.READY, ClientState.CONNECT);
				} else {
					log.error(String.format("%s failed to complete TLS handshake with APNs gateway.", this.getName()),
							handshakeFuture.cause());
				}
			} else {
				log.debug(String.format("%s does not require a TLS handshake.", this.getName()));
				this.advanceToStateFromOriginStates(ClientState.READY, ClientState.CONNECT);
			}
		} else {
			log.error(String.format("%s failed to connect to APNs gateway.", this.getName()), connectFuture.cause());
		}
	}
	
	protected void handleRejectedNotification(final RejectedNotification rejectedNotification) {
		this.reconnect();
		
		// SHUTDOWN errors from Apple are harmless; nothing bad happened with the delivered notification, so
		// we don't want to notify listeners of the error (but we still do need to reconnect).
		if (rejectedNotification.getReason() != RejectedNotificationReason.SHUTDOWN) {
			this.pushManager.notifyListenersOfRejectedNotification(
					this.getSentNotificationBuffer().getAndRemoveNotificationWithSequenceNumber(
							rejectedNotification.getSequenceNumber()), rejectedNotification.getReason());
		}
		
		this.pushManager.enqueueAllNotifications(
				this.sentNotificationBuffer.getAndRemoveAllNotificationsAfterSequenceNumber(rejectedNotification.getSequenceNumber()));
	}
	
	protected void reconnect() {
		log.debug(String.format("%s attempting to reconnect.", this.getName()));
		
		if (this.advanceToStateFromOriginStates(ClientState.RECONNECT, ClientState.READY)) {
			this.interrupt();
		}
	}
	
	/**
	 * Gracefully and asynchronously shuts down this client thread.
	 */
	public void shutdown() {
		log.debug(String.format("%s shutting down.", this.getName()));
		
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
	 * @return true if the client is connecting to the APNS server, or already connected.
	 */
	public boolean isRunning() {
		synchronized(this.state) {
			return this.state != ClientState.EXIT && this.state != ClientState.SHUTDOWN;
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
}
