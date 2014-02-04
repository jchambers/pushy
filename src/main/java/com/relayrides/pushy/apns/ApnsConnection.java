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
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
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
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO
 * 
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
class ApnsConnection<T extends ApnsPushNotification> {

	private final PushManager<T> pushManager;

	private final String name;

	private final Bootstrap bootstrap;
	private Channel channel;

	private final AtomicInteger sequenceNumber = new AtomicInteger(0);

	private volatile boolean startedConnectionAttempt = false;
	private volatile boolean shuttingDown = false;
	private volatile boolean hasEverSentNotification = false;;

	private volatile SendableApnsPushNotification<KnownBadPushNotification> shutdownNotification;

	private final SentNotificationBuffer<T> sentNotificationBuffer;
	private static final int SENT_NOTIFICATION_BUFFER_SIZE = 4096;

	private static AtomicInteger connectionCounter = new AtomicInteger(0);

	private final Logger log = LoggerFactory.getLogger(ApnsConnection.class);

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

	private class RejectedNotificationHandler extends SimpleChannelInboundHandler<RejectedNotification> {

		private final ApnsConnection<T> clientThread;

		public RejectedNotificationHandler(final ApnsConnection<T> clientThread) {
			this.clientThread = clientThread;
		}

		@Override
		protected void channelRead0(final ChannelHandlerContext context, final RejectedNotification rejectedNotification) throws Exception {
			this.clientThread.handleRejectedNotification(rejectedNotification);
		}
	}

	private class ApnsExceptionHandler extends ChannelInboundHandlerAdapter {

		private final ApnsConnection<T> apnsConnection;

		public ApnsExceptionHandler(final ApnsConnection<T> apnsConnection) {
			this.apnsConnection = apnsConnection;
		}

		@Override
		public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
			// Since this is happening on the inbound side, the most likely case is that a read timed out or the remote
			// host closed the connection. We should log the problem, but generally assume that channel closure will be
			// handled by channelInactive.
			log.debug(String.format("%s caught an exception.", apnsConnection.name), cause);
		}

		@Override
		public void channelInactive(final ChannelHandlerContext context) {
			// TODO Notify listener that connection has closed
			// TODO Decide what to do with messages on the sent buffer
		}
	}

	/**
	 * Constructs a new APNs client thread. The thread connects to the APNs gateway in the given {@code PushManager}'s
	 * environment and reads notifications from the {@code PushManager}'s queue.
	 * 
	 * @param pushManager the {@code PushManager} from which this client thread should read environment settings and
	 * notifications
	 */
	public ApnsConnection(final PushManager<T> pushManager) {

		this.pushManager = pushManager;

		this.name = String.format("ApnsConnection-%d", ApnsConnection.connectionCounter.getAndIncrement());

		this.sentNotificationBuffer = new SentNotificationBuffer<T>(SENT_NOTIFICATION_BUFFER_SIZE);

		this.bootstrap = new Bootstrap();
		this.bootstrap.group(this.pushManager.getWorkerGroup());
		this.bootstrap.channel(NioSocketChannel.class);
		this.bootstrap.option(ChannelOption.SO_KEEPALIVE, true);

		final ApnsConnection<T> clientThread = this;
		this.bootstrap.handler(new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				final ChannelPipeline pipeline = channel.pipeline();

				final SSLEngine sslEngine = pushManager.getSSLContext().createSSLEngine();
				sslEngine.setUseClientMode(true);

				pipeline.addLast("ssl", new SslHandler(sslEngine));
				pipeline.addLast("decoder", new RejectedNotificationDecoder());
				pipeline.addLast("encoder", new ApnsPushNotificationEncoder());
				pipeline.addLast("rejectionHandler", new RejectedNotificationHandler(clientThread));
				pipeline.addLast("exceptionHandler", new ApnsExceptionHandler(clientThread));
			}
		});
	}

	// TODO Remove call to setAutoClose when Netty 5.0 is available
	@SuppressWarnings("deprecation")
	public synchronized void connect() {

		final String connectionName = this.name;

		if (this.startedConnectionAttempt) {
			throw new IllegalStateException(String.format("%s already started a connection attempt.", connectionName));
		}

		this.startedConnectionAttempt = true;

		log.debug(String.format("%s beginning connection process.", connectionName));
		this.bootstrap.connect(
				this.pushManager.getEnvironment().getApnsGatewayHost(),
				this.pushManager.getEnvironment().getApnsGatewayPort()).addListener(
						new GenericFutureListener<ChannelFuture>() {

							public void operationComplete(final ChannelFuture connectFuture) {
								if (connectFuture.isSuccess()) {
									log.debug(String.format("%s connected; waiting for TLS handshake.", connectionName));

									final SslHandler sslHandler = connectFuture.channel().pipeline().get(SslHandler.class);

									if (sslHandler != null) {
										sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<Channel>>() {

											public void operationComplete(final Future<Channel> handshakeFuture) {
												if (handshakeFuture.isSuccess()) {
													log.debug(String.format("%s successfully completed TLS handshake.", connectionName));

													// this.channel = this.connectFuture.channel();
													// this.channel.config().setAutoClose(false);

													// TODO Notify listeners of success
												} else {
													log.error(String.format("%s failed to complete TLS handshake with APNs gateway.", connectionName),
															handshakeFuture.cause());

													connectFuture.channel().close();

													// TODO Notify listeners of failure
												}
											}});
									} else {
										log.error(String.format("%s failed to get SSL handler and could not wait for a TLS handshake.", connectionName));

										connectFuture.channel().close();

										// TODO Notify listeners of failure
									}
								} else {
									log.error(String.format("%s failed to connect to APNs gateway.", connectionName),
											connectFuture.cause());

									// TODO Notify listeners of failure
								}
							}
						});
	}

	public void sendNotification(final T notification) {
		final SendableApnsPushNotification<T> sendableNotification =
				new SendableApnsPushNotification<T>(notification, this.sequenceNumber.getAndIncrement());

		final String connectionName = this.name;

		if (this.channel == null || !this.channel.isActive()) {
			throw new IllegalStateException(String.format("%s is not connected.", connectionName));
		}

		if (this.shuttingDown) {
			throw new IllegalStateException(String.format("%s is shutting down.", connectionName));
		}

		if (log.isTraceEnabled()) {
			log.trace(String.format("%s sending %s", connectionName, sendableNotification));
		}

		this.channel.writeAndFlush(sendableNotification).addListener(new GenericFutureListener<ChannelFuture>() {

			public void operationComplete(final ChannelFuture future) {
				if (future.isSuccess()) {
					if (log.isTraceEnabled()) {
						log.trace(String.format("%s successfully wrote notification %d",
								connectionName, sendableNotification.getSequenceNumber()));
					}

					sentNotificationBuffer.addSentNotification(sendableNotification);
				} else {
					if (log.isTraceEnabled()) {
						log.trace(String.format("%s failed to write notification %s",
								connectionName, sendableNotification), future.cause());
					}

					// Double-check to make sure we don't have a rejected notification or remotely-closed connection
					future.channel().read();

					// Assume this is a temporary failure (we know it's not a permanent rejection because we didn't
					// even manage to write the notification to the wire) and re-enqueue for another send attempt.
					pushManager.enqueuePushNotificationForRetry(sendableNotification.getPushNotification());
				}
			}
		});

		this.hasEverSentNotification = true;
	}

	private void handleRejectedNotification(final RejectedNotification rejectedNotification) {

		log.debug(String.format("APNs gateway rejected notification with sequence number %d from %s (%s).",
				rejectedNotification.getSequenceNumber(), this.name, rejectedNotification.getReason()));

		this.sentNotificationBuffer.clearNotificationsBeforeSequenceNumber(rejectedNotification.getSequenceNumber());

		// Notify listeners of the rejected notification, but only if it's not a known-bad shutdown notification
		if (this.shutdownNotification == null || rejectedNotification.getSequenceNumber() != this.shutdownNotification.getSequenceNumber()) {
			// SHUTDOWN errors from Apple are harmless; nothing bad happened with the delivered notification, so
			// we don't want to notify listeners of the error (but we still do need to reconnect).
			if (rejectedNotification.getReason() != RejectedNotificationReason.SHUTDOWN) {

				final T notification = this.sentNotificationBuffer.getNotificationWithSequenceNumber(
						rejectedNotification.getSequenceNumber());

				if (notification != null) {
					this.pushManager.notifyListenersOfRejectedNotification(notification, rejectedNotification.getReason());
				} else {
					log.error(String.format("%s failed to find rejected notification with sequence number %d; this " +
							"most likely means the sent notification buffer is too small. Please report this as a bug.",
							this.name, rejectedNotification.getSequenceNumber()));
				}
			}
		}

		// In any case, we're confident that all notifications sent before the rejected notification were processed and
		// NOT rejected, while all notifications after the rejected one have definitely not been processed and need to
		// be re-sent.
		this.pushManager.enqueueAllNotificationsForRetry(
				this.sentNotificationBuffer.getAllNotificationsAfterSequenceNumber(
						rejectedNotification.getSequenceNumber()));
	}

	/**
	 * Gracefully and asynchronously shuts down this client thread.
	 */
	protected synchronized void shutdownGracefully() {

		final ApnsConnection<T> apnsConnection = this;

		this.shuttingDown = true;

		if (this.hasEverSentNotification && this.shutdownNotification == null) {

			// It's conceivable that the channel has become inactive already; if so, our work here is already done.
			if (this.channel.isActive()) {

				this.shutdownNotification = new SendableApnsPushNotification<KnownBadPushNotification>(
						new KnownBadPushNotification(), this.sequenceNumber.getAndIncrement());

				if (log.isTraceEnabled()) {
					log.trace(String.format("%s sending known-bad notification to shut down.", apnsConnection.name));
				}

				this.channel.writeAndFlush(this.shutdownNotification).addListener(new GenericFutureListener<ChannelFuture>() {

					public void operationComplete(final ChannelFuture future) {
						if (future.isSuccess()) {
							if (log.isTraceEnabled()) {
								log.trace(String.format("%s successfully wrote known-bad notification %d",
										apnsConnection.name, apnsConnection.shutdownNotification.getSequenceNumber()));
							}
						} else {
							if (log.isTraceEnabled()) {
								log.trace(String.format("%s failed to write known-bad notification %s",
										apnsConnection.name, apnsConnection.shutdownNotification), future.cause());
							}

							// Try again!
							apnsConnection.shutdownNotification = null;
							apnsConnection.shutdownGracefully();
						}
					}
				});
			}

		} else {
			this.shutdownImmediately();
		}
	}

	protected synchronized void shutdownImmediately() {
		this.shuttingDown = true;

		if (this.channel != null) {
			// TODO Decide how to synchronize on this
			this.channel.close();
		}
	}
}
