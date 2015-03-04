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
import io.netty.buffer.PooledByteBufAllocator;
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
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>A connection to an APNs gateway. An {@code ApnsConnection} is responsible for sending push notifications to the
 * APNs gateway, and reports lifecycle events via its {@link ApnsConnectionListener}.</p>
 *
 * <p>Generally, connections should be managed by a parent {@link PushManager} and not manipulated directly (although
 * connections are fully functional on their own). Connections are created in a disconnected state, and must be
 * explicitly connected before they can be used to send push notifications.</p>
 *
 * @see PushManager
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class ApnsConnection<T extends ApnsPushNotification> {

	private final ApnsEnvironment environment;
	private final SSLContext sslContext;
	private final NioEventLoopGroup eventLoopGroup;
	private final ApnsConnectionConfiguration configuration;
	private final ApnsConnectionListener<T> listener;

	private final String name;

	private final Object channelRegistrationMonitor = new Object();
	private ChannelFuture connectFuture;
	private volatile boolean handshakeCompleted = false;
	private volatile boolean closeOnRegistration;

	// We want to start the count at 1 here because the gateway will send back a sequence number of 0 if it doesn't know
	// which notification failed. This isn't 100% bulletproof (we'll legitimately get back to 0 after 2^32
	// notifications), but the probability of collision (or even sending 4 billion notifications without some recipient
	// having an expired token) is vanishingly small.
	private int sequenceNumber = 1;

	private final Object pendingWriteMonitor = new Object();
	private int pendingWriteCount = 0;
	private int sendAttempts = 0;

	private SendableApnsPushNotification<KnownBadPushNotification> shutdownNotification;

	private boolean rejectionReceived = false;
	private final SentNotificationBuffer<T> sentNotificationBuffer;

	private static final String PIPELINE_MAIN_HANDLER = "handler";
	private static final String PIPELINE_IDLE_STATE_HANDLER = "idleStateHandler";
	private static final String PIPELINE_GRACEFUL_SHUTDOWN_TIMEOUT_HANDLER = "gracefulShutdownTimeoutHandler";

	private static final Logger log = LoggerFactory.getLogger(ApnsConnection.class);

	public static final int DEFAULT_SENT_NOTIFICATION_BUFFER_CAPACITY = 8192;

	protected enum ApnsFrameItem {
		DEVICE_TOKEN((byte)1),
		PAYLOAD((byte)2),
		SEQUENCE_NUMBER((byte)3),
		DELIVERY_INVALIDATION_TIME((byte)4),
		PRIORITY((byte)5);

		private final byte code;

		private ApnsFrameItem(final byte code) {
			this.code = code;
		}

		protected byte getCode() {
			return this.code;
		}

		protected static ApnsFrameItem getFrameItemFromCode(final byte code) {
			for (final ApnsFrameItem item : ApnsFrameItem.values()) {
				if (item.getCode() == code) {
					return item;
				}
			}

			throw new IllegalArgumentException(String.format("No frame item found with code %d", code));
		}
	}

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
					log.error("Unexpected command: {}", command);
				}

				final RejectedNotificationReason errorCode = RejectedNotificationReason.getByErrorCode(code);

				out.add(new RejectedNotification(notificationId, errorCode));
			}
		}
	}

	private class ApnsPushNotificationEncoder extends MessageToByteEncoder<SendableApnsPushNotification<T>> {

		private static final byte BINARY_PUSH_NOTIFICATION_COMMAND = 2;
		private static final int INVALIDATE_IMMEDIATELY = 0;

		private static final int FRAME_ITEM_ID_SIZE = 1;
		private static final int FRAME_ITEM_LENGTH_SIZE = 2;

		private static final short SEQUENCE_NUMBER_SIZE = 4;
		private static final short DELIVERY_INVALIDATION_TIME_SIZE = 4;
		private static final short PRIORITY_SIZE = 1;

		private final Charset utf8 = Charset.forName("UTF-8");

		@Override
		protected void encode(final ChannelHandlerContext context, final SendableApnsPushNotification<T> sendablePushNotification, final ByteBuf out) throws Exception {
			out.writeByte(BINARY_PUSH_NOTIFICATION_COMMAND);
			out.writeInt(this.getFrameLength(sendablePushNotification));

			out.writeByte(ApnsFrameItem.SEQUENCE_NUMBER.getCode());
			out.writeShort(SEQUENCE_NUMBER_SIZE);
			out.writeInt(sendablePushNotification.getSequenceNumber());

			out.writeByte(ApnsFrameItem.DEVICE_TOKEN.getCode());
			out.writeShort(sendablePushNotification.getPushNotification().getToken().length);
			out.writeBytes(sendablePushNotification.getPushNotification().getToken());

			final byte[] payloadBytes = sendablePushNotification.getPushNotification().getPayload().getBytes(utf8);

			out.writeByte(ApnsFrameItem.PAYLOAD.getCode());
			out.writeShort(payloadBytes.length);
			out.writeBytes(payloadBytes);

			out.writeByte(ApnsFrameItem.DELIVERY_INVALIDATION_TIME.getCode());
			out.writeShort(DELIVERY_INVALIDATION_TIME_SIZE);

			final int deliveryInvalidationTime;

			if (sendablePushNotification.getPushNotification().getDeliveryInvalidationTime() != null) {
				deliveryInvalidationTime = this.getTimestampInSeconds(
						sendablePushNotification.getPushNotification().getDeliveryInvalidationTime());
			} else {
				deliveryInvalidationTime = INVALIDATE_IMMEDIATELY;
			}

			out.writeInt(deliveryInvalidationTime);

			final DeliveryPriority priority = sendablePushNotification.getPushNotification().getPriority() != null ?
					sendablePushNotification.getPushNotification().getPriority() : DeliveryPriority.IMMEDIATE;

					out.writeByte(ApnsFrameItem.PRIORITY.getCode());
					out.writeShort(PRIORITY_SIZE);
					out.writeByte(priority.getCode());
		}

		private int getTimestampInSeconds(final Date date) {
			return (int)(date.getTime() / 1000);
		}

		private int getFrameLength(final SendableApnsPushNotification<T> sendableApnsPushNotification) {
			return	ApnsFrameItem.values().length * (FRAME_ITEM_ID_SIZE + FRAME_ITEM_LENGTH_SIZE) +
					sendableApnsPushNotification.getPushNotification().getToken().length +
					sendableApnsPushNotification.getPushNotification().getPayload().getBytes(utf8).length +
					SEQUENCE_NUMBER_SIZE +
					DELIVERY_INVALIDATION_TIME_SIZE +
					PRIORITY_SIZE;
		}
	}

	private class ApnsConnectionHandler extends SimpleChannelInboundHandler<RejectedNotification> {

		private final ApnsConnection<T> apnsConnection;

		public ApnsConnectionHandler(final ApnsConnection<T> apnsConnection) {
			this.apnsConnection = apnsConnection;
		}

		@Override
		public void channelRegistered(final ChannelHandlerContext context) throws Exception {
			super.channelRegistered(context);

			synchronized (this.apnsConnection.channelRegistrationMonitor) {
				if (this.apnsConnection.closeOnRegistration) {
					log.debug("Channel registered for {}, but shutting down immediately.", this.apnsConnection.name);
					context.channel().eventLoop().execute(this.apnsConnection.getImmediateShutdownRunnable());
				}
			}
		}

		@Override
		protected void channelRead0(final ChannelHandlerContext context, final RejectedNotification rejectedNotification) {
			log.debug("APNs gateway rejected notification with sequence number {} from {} ({}).",
					rejectedNotification.getSequenceNumber(), this.apnsConnection.name, rejectedNotification.getReason());

			this.apnsConnection.rejectionReceived = true;
			this.apnsConnection.sentNotificationBuffer.clearNotificationsBeforeSequenceNumber(rejectedNotification.getSequenceNumber());

			final boolean isKnownBadRejection = this.apnsConnection.shutdownNotification != null &&
					(rejectedNotification.getSequenceNumber() == this.apnsConnection.shutdownNotification.getSequenceNumber()
					|| (rejectedNotification.getSequenceNumber() == 0 && RejectedNotificationReason.MISSING_TOKEN.equals(rejectedNotification.getReason())));

			// We only want to notify listeners of an actual rejection if something actually went wrong. We don't want
			// to notify listeners if a known-bad notification was rejected because that's an expected case, and we
			// don't want to notify listeners if the gateway is shutting down the connection, but still processed the
			// named notification successfully.
			if (!isKnownBadRejection && !RejectedNotificationReason.SHUTDOWN.equals(rejectedNotification.getReason())) {
				final T notification = this.apnsConnection.sentNotificationBuffer.getNotificationWithSequenceNumber(
						rejectedNotification.getSequenceNumber());

				if (notification != null) {
					if (this.apnsConnection.listener != null) {
						this.apnsConnection.listener.handleRejectedNotification(
								this.apnsConnection, notification, rejectedNotification.getReason());
					}
				} else {
					if (this.apnsConnection.sentNotificationBuffer.isEmpty()) {
						log.error("{} failed to find rejected notification with sequence number {} (buffer is empty). " +
								"this may mean the sent notification buffer is too small. Please report this as a bug.",
								this.apnsConnection.name, rejectedNotification.getSequenceNumber());
					} else {
						log.error("{} failed to find rejected notification with sequence number {} (buffer has range {} to " +
								"{}); this may mean the sent notification buffer is too small. Please report this as a bug.",
								this.apnsConnection.name, rejectedNotification.getSequenceNumber(),
								this.apnsConnection.sentNotificationBuffer.getLowestSequenceNumber(),
								this.apnsConnection.sentNotificationBuffer.getHighestSequenceNumber());
					}
				}
			}

			// Regardless of the cause, we ALWAYS want to notify listeners that some sent notifications were not
			// processed by the gateway (assuming there are some such notifications). The exception here is an upstream
			// bug where the sequence number will be incorrectly reported as zero when sending a zero-length token (i.e.
			// a known-bad shutdown token). In that case we don't know the actual sequence number, and can't determine
			// what was sent after the bad notification.
			if (rejectedNotification.getSequenceNumber() != 0) {
				final Collection<T> unprocessedNotifications =
						this.apnsConnection.sentNotificationBuffer.getAllNotificationsAfterSequenceNumber(
								rejectedNotification.getSequenceNumber());

				if (!unprocessedNotifications.isEmpty()) {
					if (this.apnsConnection.listener != null) {
						this.apnsConnection.listener.handleUnprocessedNotifications(this.apnsConnection, unprocessedNotifications);
					}
				}
			}

			this.apnsConnection.sentNotificationBuffer.clearAllNotifications();
		}

		@Override
		public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
			// Since this is happening on the inbound side, the most likely case is that a read timed out or the remote
			// host closed the connection. We should log the problem, but generally assume that channel closure will be
			// handled by channelInactive.
			log.debug("{} caught an exception.", this.apnsConnection.name, cause);
		}

		@Override
		public void channelInactive(final ChannelHandlerContext context) throws Exception {
			super.channelInactive(context);

			// Channel closure implies that the connection attempt had fully succeeded, so we only want to notify
			// listeners if the handshake has completed. Otherwise, we'll notify listeners of a connection failure (as
			// opposed to closure) elsewhere.
			if (this.apnsConnection.handshakeCompleted) {
				if (this.apnsConnection.listener != null) {
					this.apnsConnection.listener.handleConnectionClosure(this.apnsConnection);
				}
			}
		}

		@Override
		public void channelWritabilityChanged(final ChannelHandlerContext context) throws Exception {
			super.channelWritabilityChanged(context);

			if (this.apnsConnection.listener != null) {
				this.apnsConnection.listener.handleConnectionWritabilityChange(
						this.apnsConnection, context.channel().isWritable());
			}
		}

		@Override
		public void userEventTriggered(final ChannelHandlerContext context, final Object event) throws Exception {
			if (event instanceof IdleStateEvent) {
				// The IdleStateHandler for connection inactivity is removed by shutdownGracefully, which also populates
				// shutdownNotification. If we get an IdleStateEvent without a shutdownNotification, we know that the
				// event came from the connection inactivity handler. Otherwise, we know it came from the graceful
				// shutdown timeout handler.
				if (this.apnsConnection.shutdownNotification == null) {
					log.debug("{} will shut down gracefully due to inactivity.", this.apnsConnection.name);
					this.apnsConnection.shutdownGracefully();
				} else {
					log.debug("Graceful shutdown attempt for {} timed out; shutting down immediately.", this.apnsConnection.name);
					this.apnsConnection.shutdownImmediately();
				}
			} else {
				super.userEventTriggered(context, event);
			}
		}
	}

	/**
	 * Constructs a new APNs connection. The connection connects to the APNs gateway in the given environment with the
	 * credentials and key/trust managers in the given SSL context.
	 *
	 * @param environment the environment in which this connection will operate; must not be {@code null}
	 * @param sslContext an SSL context with the keys/certificates and trust managers this connection should use when
	 * communicating with the APNs gateway; must not be {@code null}
	 * @param eventLoopGroup the event loop group this connection should use for asynchronous network operations; must
	 * not be {@code null}
	 * @param configuration the set of configuration options to use for this connection. The configuration object is
	 * copied and changes to the original object will not propagate to the connection after creation. Must not be
	 * {@code null}.
	 * @param listener the listener to which this connection will report lifecycle events; may be {@code null}
	 * @param name a human-readable name for this connection; names must not be {@code null}
	 */
	public ApnsConnection(final ApnsEnvironment environment, final SSLContext sslContext,
			final NioEventLoopGroup eventLoopGroup, final ApnsConnectionConfiguration configuration,
			final ApnsConnectionListener<T> listener, final String name) {

		if (environment == null) {
			throw new NullPointerException("Environment must not be null.");
		}

		this.environment = environment;

		if (sslContext == null) {
			throw new NullPointerException("SSL context must not be null.");
		}

		this.sslContext = sslContext;

		if (eventLoopGroup == null) {
			throw new NullPointerException("Event loop group must not be null.");
		}

		this.eventLoopGroup = eventLoopGroup;

		if (configuration == null) {
			throw new NullPointerException("Connection configuration must not be null.");
		}

		this.configuration = configuration;
		this.listener = listener;

		if (name == null) {
			throw new NullPointerException("Connection name must not be null.");
		}

		this.name = name;

		this.sentNotificationBuffer = new SentNotificationBuffer<T>(configuration.getSentNotificationBufferCapacity());
	}

	/**
	 * Asynchronously connects to the APNs gateway in this connection's environment. The outcome of the connection
	 * attempt is reported via this connection's listener.
	 *
	 * @see ApnsConnectionListener#handleConnectionSuccess(ApnsConnection)
	 * @see ApnsConnectionListener#handleConnectionFailure(ApnsConnection, Throwable)
	 */
	@SuppressWarnings("deprecation")
	public synchronized void connect() {

		final ApnsConnection<T> apnsConnection = this;

		if (this.connectFuture != null) {
			throw new IllegalStateException(String.format("%s already started a connection attempt.", this.name));
		}

		final Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(this.eventLoopGroup);
		bootstrap.channel(NioSocketChannel.class);
		bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);

		// TODO Remove this when Netty 5 is available
		bootstrap.option(ChannelOption.AUTO_CLOSE, false);

		bootstrap.handler(new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(final SocketChannel channel) {
				final ChannelPipeline pipeline = channel.pipeline();

				final SSLEngine sslEngine = apnsConnection.sslContext.createSSLEngine();
				sslEngine.setUseClientMode(true);

				pipeline.addLast("ssl", new SslHandler(sslEngine));
				pipeline.addLast("decoder", new RejectedNotificationDecoder());
				pipeline.addLast("encoder", new ApnsPushNotificationEncoder());
				pipeline.addLast(ApnsConnection.PIPELINE_MAIN_HANDLER, new ApnsConnectionHandler(apnsConnection));
			}
		});

		log.debug("{} beginning connection process.", apnsConnection.name);
		this.connectFuture = bootstrap.connect(this.environment.getApnsGatewayHost(), this.environment.getApnsGatewayPort());
		this.connectFuture.addListener(new GenericFutureListener<ChannelFuture>() {

			@Override
			public void operationComplete(final ChannelFuture connectFuture) {
				if (connectFuture.isSuccess()) {
					log.debug("{} connected; waiting for TLS handshake.", apnsConnection.name);

					final SslHandler sslHandler = connectFuture.channel().pipeline().get(SslHandler.class);

					try {
						sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<Channel>>() {

							@Override
							public void operationComplete(final Future<Channel> handshakeFuture) {
								if (handshakeFuture.isSuccess()) {
									log.debug("{} successfully completed TLS handshake.", apnsConnection.name);

									apnsConnection.handshakeCompleted = true;

									if (apnsConnection.listener != null) {
										apnsConnection.listener.handleConnectionSuccess(apnsConnection);
									}

									if (apnsConnection.configuration.getCloseAfterInactivityTime() != null) {
										connectFuture.channel().pipeline().addBefore(ApnsConnection.PIPELINE_MAIN_HANDLER,
												ApnsConnection.PIPELINE_IDLE_STATE_HANDLER,
												new IdleStateHandler(0, 0, apnsConnection.configuration.getCloseAfterInactivityTime()));
									}

								} else {
									log.debug("{} failed to complete TLS handshake with APNs gateway.",
											apnsConnection.name, handshakeFuture.cause());

									connectFuture.channel().close();

									if (apnsConnection.listener != null) {
										apnsConnection.listener.handleConnectionFailure(apnsConnection, handshakeFuture.cause());
									}
								}
							}});
					} catch (NullPointerException e) {
						log.warn("{} failed to get SSL handler and could not wait for a TLS handshake.", apnsConnection.name);

						connectFuture.channel().close();

						if (apnsConnection.listener != null) {
							apnsConnection.listener.handleConnectionFailure(apnsConnection, e);
						}
					}
				} else {
					log.debug("{} failed to connect to APNs gateway.", apnsConnection.name, connectFuture.cause());

					if (apnsConnection.listener != null) {
						apnsConnection.listener.handleConnectionFailure(apnsConnection, connectFuture.cause());
					}
				}
			}
		});
	}

	/**
	 * Asynchronously sends a push notification to the connected APNs gateway. Successful notifications are
	 * <strong>not</strong> acknowledged by the APNs gateway; failed attempts to write push notifications to the
	 * outbound buffer and notification rejections are reported via this connection's listener.
	 *
	 * @param notification the notification to send
	 *
	 * @see ApnsConnectionListener#handleWriteFailure(ApnsConnection, ApnsPushNotification, Throwable)
	 * @see ApnsConnectionListener#handleRejectedNotification(ApnsConnection, ApnsPushNotification, RejectedNotificationReason)
	 */
	public synchronized void sendNotification(final T notification) {
		final ApnsConnection<T> apnsConnection = this;

		if (!this.handshakeCompleted) {
			throw new IllegalStateException(String.format("%s has not completed handshake.", this.name));
		}

		if (this.shutdownNotification == null) {
			this.connectFuture.channel().eventLoop().execute(new Runnable() {

				@Override
				public void run() {
					final SendableApnsPushNotification<T> sendableNotification =
							new SendableApnsPushNotification<T>(notification, apnsConnection.sequenceNumber++);

					log.trace("{} sending {}", apnsConnection.name, sendableNotification);

					apnsConnection.pendingWriteCount += 1;

					apnsConnection.connectFuture.channel().writeAndFlush(sendableNotification).addListener(new GenericFutureListener<ChannelFuture>() {

						@Override
						public void operationComplete(final ChannelFuture writeFuture) {
							if (writeFuture.isSuccess()) {
								log.trace("{} successfully wrote notification {}", apnsConnection.name,
										sendableNotification.getSequenceNumber());

								if (apnsConnection.rejectionReceived) {
									// Even though the write succeeded, we know for sure that this notification was never
									// processed by the gateway because it had already rejected another notification from
									// this connection.
									if (apnsConnection.listener != null) {
										apnsConnection.listener.handleUnprocessedNotifications(apnsConnection, java.util.Collections.singletonList(notification));
									}
								} else {
									apnsConnection.sentNotificationBuffer.addSentNotification(sendableNotification);
								}
							} else {
								log.trace("{} failed to write notification {}",
										apnsConnection.name, sendableNotification, writeFuture.cause());

								// Assume this is a temporary failure (we know it's not a permanent rejection because we didn't
								// even manage to write the notification to the wire) and re-enqueue for another send attempt.
								if (apnsConnection.listener != null) {
									apnsConnection.listener.handleWriteFailure(apnsConnection, notification, writeFuture.cause());
								}
							}

							apnsConnection.pendingWriteCount -= 1;
							assert apnsConnection.pendingWriteCount >= 0;

							if (apnsConnection.pendingWriteCount == 0) {
								synchronized (apnsConnection.pendingWriteMonitor) {
									apnsConnection.pendingWriteMonitor.notifyAll();
								}
							}
						}
					});
				}
			});
		} else {
			if (this.listener != null) {
				this.listener.handleWriteFailure(this, notification, new IllegalStateException("Connection is shutting down."));
			}
		}

		if (this.configuration.getSendAttemptLimit() != null && ++this.sendAttempts >= this.configuration.getSendAttemptLimit()) {
			log.debug("{} reached send attempt limit and will shut down gracefully.", this.name);
			this.shutdownGracefully();
		}
	}

	/**
	 * <p>Waits for all pending write operations to finish. When this method exits normally (i.e. when it does
	 * not throw an {@code InterruptedException}), All pending writes will have either finished successfully or failed
	 * and passed to this connection's listener via the
	 * {@link ApnsConnectionListener#handleWriteFailure(ApnsConnection, ApnsPushNotification, Throwable)} method.</p>
	 *
	 * <p>It is <em>not</em> guaranteed that all write operations will have finished by the time a connection has
	 * closed. Applications that need to know when all writes have finished should call this method after a connection
	 * closes, but must not do so in an IO thread (i.e. the thread that called the
	 * {@link ApnsConnectionListener#handleConnectionClosure(ApnsConnection)} method.</p>
	 *
	 * @throws InterruptedException if interrupted while waiting for pending read/write operations to finish
	 */
	public void waitForPendingWritesToFinish() throws InterruptedException {
		synchronized (this.pendingWriteMonitor) {
			while (this.pendingWriteCount > 0) {
				this.pendingWriteMonitor.wait();
			}
		}
	}

	/**
	 * <p>Gracefully and asynchronously shuts down this connection. Graceful disconnection is triggered by sending a
	 * known-bad notification to the APNs gateway; when the gateway rejects the notification, it is guaranteed that
	 * preceding notifications were processed successfully and that all following notifications were not processed at
	 * all. The gateway will close the connection after rejecting the notification, and this connection's listener will
	 * be notified when the connection is closed.</p>
	 *
	 * <p>Note that if/when the known-bad notification is rejected by the APNs gateway, this connection's listener will
	 * <em>not</em> be notified of the rejection.</p>
	 *
	 * <p>Calling this method before establishing a connection with the APNs gateway or while a graceful shutdown
	 * attempt is already in progress has no effect.</p>
	 *
	 * @see ApnsConnectionListener#handleRejectedNotification(ApnsConnection, ApnsPushNotification, RejectedNotificationReason)
	 * @see ApnsConnectionListener#handleConnectionClosure(ApnsConnection)
	 */
	public synchronized void shutdownGracefully() {

		if (this.connectFuture != null && this.connectFuture.channel() != null) {
			if (this.connectFuture.channel().pipeline().get(ApnsConnection.PIPELINE_IDLE_STATE_HANDLER) != null) {
				this.connectFuture.channel().pipeline().remove(ApnsConnection.PIPELINE_IDLE_STATE_HANDLER);
			}
		}

		final ApnsConnection<T> apnsConnection = this;

		// We only need to send a known-bad notification if we were ever connected in the first place and if we're
		// still connected.
		if (this.handshakeCompleted && this.connectFuture.channel().isActive()) {

			this.connectFuture.channel().eventLoop().execute(new Runnable() {

				@Override
				public void run() {
					// Don't send a second shutdown notification if we've already started the graceful shutdown process.
					if (apnsConnection.shutdownNotification == null) {

						log.debug("{} sending known-bad notification to shut down.", apnsConnection.name);

						apnsConnection.shutdownNotification = new SendableApnsPushNotification<KnownBadPushNotification>(
								new KnownBadPushNotification(), apnsConnection.sequenceNumber++);

						if (apnsConnection.configuration.getGracefulShutdownTimeout() != null &&
								apnsConnection.connectFuture.channel().pipeline().get(PIPELINE_GRACEFUL_SHUTDOWN_TIMEOUT_HANDLER) == null) {
							// We should time out, but haven't added an idle state handler yet.
							apnsConnection.connectFuture.channel().pipeline().addBefore(
									PIPELINE_MAIN_HANDLER,
									PIPELINE_GRACEFUL_SHUTDOWN_TIMEOUT_HANDLER,
									new IdleStateHandler(apnsConnection.configuration.getGracefulShutdownTimeout(), 0, 0));
						}


						apnsConnection.pendingWriteCount += 1;

						apnsConnection.connectFuture.channel().writeAndFlush(apnsConnection.shutdownNotification).addListener(new GenericFutureListener<ChannelFuture>() {

							@Override
							public void operationComplete(final ChannelFuture future) {
								if (future.isSuccess()) {
									log.trace("{} successfully wrote known-bad notification {}",
											apnsConnection.name, apnsConnection.shutdownNotification.getSequenceNumber());
								} else {
									log.trace("{} failed to write known-bad notification {}",
											apnsConnection.name, apnsConnection.shutdownNotification, future.cause());

									// Try again!
									apnsConnection.shutdownNotification = null;
									apnsConnection.shutdownGracefully();
								}

								apnsConnection.pendingWriteCount -= 1;
								assert apnsConnection.pendingWriteCount >= 0;

								if (apnsConnection.pendingWriteCount == 0) {
									synchronized (apnsConnection.pendingWriteMonitor) {
										apnsConnection.pendingWriteMonitor.notifyAll();
									}
								}
							}
						});
					}
				}
			});
		} else {
			// While we can't guarantee that the handshake won't complete in another thread, we CAN guarantee that no
			// new notifications will be sent until shutdownImmediately happens because everything is synchronized.
			this.shutdownImmediately();
		}
	}

	/**
	 * <p>Immediately closes this connection (assuming it was ever open). No guarantees are made with regard to the
	 * state of sent notifications, and callers should generally prefer {@link ApnsConnection#shutdownGracefully} to
	 * this method. If the connection was previously open, the connection's listener will be notified of the
	 * connection's closure. If a connection attempt was in progress, the listener will be notified of a connection
	 * failure. If the connection was never open, this method has no effect.</p>
	 *
	 * <p>Calling this method while not connected has no effect.</p>
	 *
	 * @see ApnsConnectionListener#handleConnectionClosure(ApnsConnection)
	 */
	public synchronized void shutdownImmediately() {
		if (this.connectFuture != null) {
			synchronized (this.channelRegistrationMonitor) {
				if (this.connectFuture.channel().isRegistered()) {
					this.connectFuture.channel().eventLoop().execute(this.getImmediateShutdownRunnable());
				} else {
					this.closeOnRegistration = true;
				}
			}
		}
	}

	private Runnable getImmediateShutdownRunnable() {
		final ApnsConnection<T> apnsConnection = this;

		return new Runnable() {
			@Override
			public void run() {
				final SslHandler sslHandler = apnsConnection.connectFuture.channel().pipeline().get(SslHandler.class);

				if (apnsConnection.connectFuture.isCancellable()) {
					apnsConnection.connectFuture.cancel(true);
				} else if (sslHandler != null && sslHandler.handshakeFuture().isCancellable()) {
					sslHandler.handshakeFuture().cancel(true);
				} else {
					apnsConnection.connectFuture.channel().close();
				}
			}
		};
	}

	@Override
	public String toString() {
		return "ApnsConnection [name=" + name + "]";
	}
}
