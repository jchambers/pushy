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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
class ApnsConnection<T extends ApnsPushNotification> {

	private final ApnsEnvironment environment;
	private final SSLContext sslContext;
	private final NioEventLoopGroup eventLoopGroup;
	private final ApnsConnectionListener<T> listener;

	private static final AtomicInteger connectionCounter = new AtomicInteger(0);
	private final String name;

	private ChannelFuture connectFuture;
	private volatile boolean handshakeCompleted = false;
	private boolean closeOnRegistration;

	private int sequenceNumber = 0;

	private final Object pendingWriteMonitor = new Object();
	private int pendingWriteCount = 0;

	private SendableApnsPushNotification<KnownBadPushNotification> shutdownNotification;

	private boolean rejectionReceived = false;
	private final SentNotificationBuffer<T> sentNotificationBuffer = new SentNotificationBuffer<T>(4096);

	private static final Logger log = LoggerFactory.getLogger(ApnsConnection.class);

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

	private class ApnsConnectionHandler extends SimpleChannelInboundHandler<RejectedNotification> {

		private final ApnsConnection<T> apnsConnection;

		public ApnsConnectionHandler(final ApnsConnection<T> clientThread) {
			this.apnsConnection = clientThread;
		}

		@Override
		public void channelRegistered(final ChannelHandlerContext context) throws Exception {
			super.channelRegistered(context);

			synchronized (this.apnsConnection.connectFuture) {
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
					rejectedNotification.getSequenceNumber() == this.apnsConnection.shutdownNotification.getSequenceNumber();

			// We only want to notify listeners of an actual rejection if something actually went wrong. We don't want
			// to notify listeners if a known-bad notification was rejected because that's an expected case, and we
			// don't want to notify listeners if the gateway is shutting down the connection, but still processed the
			// named notification successfully.
			if (!isKnownBadRejection && !RejectedNotificationReason.SHUTDOWN.equals(rejectedNotification.getReason())) {
				final T notification = this.apnsConnection.sentNotificationBuffer.getNotificationWithSequenceNumber(
						rejectedNotification.getSequenceNumber());

				if (notification != null) {
					this.apnsConnection.listener.handleRejectedNotification(
							this.apnsConnection, notification, rejectedNotification.getReason());
				} else {
					log.error("{} failed to find rejected notification with sequence number {}; this may mean the " +
							"sent notification buffer is too small. Please report this as a bug.",
							this.apnsConnection.name, rejectedNotification.getSequenceNumber());
				}
			}

			// Regardless of the cause, we ALWAYS want to notify listeners that some sent notifications were not
			// processed by the gateway (assuming there are some such notifications).
			final Collection<T> unprocessedNotifications =
					this.apnsConnection.sentNotificationBuffer.getAllNotificationsAfterSequenceNumber(
							rejectedNotification.getSequenceNumber());

			if (!unprocessedNotifications.isEmpty()) {
				this.apnsConnection.listener.handleUnprocessedNotifications(this.apnsConnection, unprocessedNotifications);
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
				this.apnsConnection.listener.handleConnectionClosure(this.apnsConnection);
			}
		}

		@Override
		public void channelWritabilityChanged(final ChannelHandlerContext context) throws Exception {
			super.channelWritabilityChanged(context);

			this.apnsConnection.listener.handleConnectionWritabilityChange(
					this.apnsConnection, context.channel().isWritable());
		}
	}

	/**
	 * Constructs a new APNs connection. The connection connects to the APNs gateway in the given environment with the
	 * credentials and key/trust managers in the given SSL context.
	 *
	 * @param environment the environment in which this connection will operate
	 * @param sslContext an SSL context with the keys/certificates and trust managers this connection should use when
	 * communicating with the APNs gateway
	 * @param eventLoopGroup the event loop group this connection should use for asynchronous network operations
	 * @param listener the listener to which this connection will report lifecycle events; must not be {@code null}
	 */
	public ApnsConnection(final ApnsEnvironment environment, final SSLContext sslContext, final NioEventLoopGroup eventLoopGroup, final ApnsConnectionListener<T> listener) {

		if (listener == null) {
			throw new NullPointerException("Listener must not be null.");
		}

		this.environment = environment;
		this.sslContext = sslContext;
		this.eventLoopGroup = eventLoopGroup;
		this.listener = listener;

		this.name = String.format("ApnsConnection-%d", ApnsConnection.connectionCounter.getAndIncrement());
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
		bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
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
				pipeline.addLast("handler", new ApnsConnectionHandler(apnsConnection));
			}
		});

		log.debug("{} beginning connection process.", apnsConnection.name);
		this.connectFuture = bootstrap.connect(this.environment.getApnsGatewayHost(), this.environment.getApnsGatewayPort());
		this.connectFuture.addListener(new GenericFutureListener<ChannelFuture>() {

			public void operationComplete(final ChannelFuture connectFuture) {
				if (connectFuture.isSuccess()) {
					log.debug("{} connected; waiting for TLS handshake.", apnsConnection.name);

					final SslHandler sslHandler = connectFuture.channel().pipeline().get(SslHandler.class);

					try {
						sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<Channel>>() {

							public void operationComplete(final Future<Channel> handshakeFuture) {
								if (handshakeFuture.isSuccess()) {
									log.debug("{} successfully completed TLS handshake.", apnsConnection.name);

									apnsConnection.handshakeCompleted = true;
									apnsConnection.listener.handleConnectionSuccess(apnsConnection);
								} else {
									log.debug("{} failed to complete TLS handshake with APNs gateway.",
											apnsConnection.name, handshakeFuture.cause());

									connectFuture.channel().close();
									apnsConnection.listener.handleConnectionFailure(apnsConnection, handshakeFuture.cause());
								}
							}});
					} catch (NullPointerException e) {
						log.warn("{} failed to get SSL handler and could not wait for a TLS handshake.", apnsConnection.name);

						connectFuture.channel().close();
						apnsConnection.listener.handleConnectionFailure(apnsConnection, e);
					}
				} else {
					log.debug("{} failed to connect to APNs gateway.", apnsConnection.name, connectFuture.cause());

					apnsConnection.listener.handleConnectionFailure(apnsConnection, connectFuture.cause());
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
	 * @see ApnsConnectionListener#handleRejectedNotification(ApnsConnection, ApnsPushNotification, RejectedNotificationReason, java.util.Collection)
	 */
	public synchronized void sendNotification(final T notification) {
		final ApnsConnection<T> apnsConnection = this;

		if (!this.handshakeCompleted) {
			throw new IllegalStateException(String.format("%s has not completed handshake.", this.name));
		}

		this.connectFuture.channel().eventLoop().execute(new Runnable() {

			public void run() {
				final SendableApnsPushNotification<T> sendableNotification =
						new SendableApnsPushNotification<T>(notification, apnsConnection.sequenceNumber++);

				log.trace("{} sending {}", apnsConnection.name, sendableNotification);

				apnsConnection.pendingWriteCount += 1;

				apnsConnection.connectFuture.channel().writeAndFlush(sendableNotification).addListener(new GenericFutureListener<ChannelFuture>() {

					public void operationComplete(final ChannelFuture writeFuture) {
						if (writeFuture.isSuccess()) {
							log.trace("{} successfully wrote notification {}", apnsConnection.name,
								sendableNotification.getSequenceNumber());

							if (apnsConnection.rejectionReceived) {
								// Even though the write succeeded, we know for sure that this notification was never
								// processed by the gateway because it had already rejected another notification from
								// this connection.
								apnsConnection.listener.handleUnprocessedNotifications(apnsConnection, java.util.Collections.singletonList(notification));
							} else {
								apnsConnection.sentNotificationBuffer.addSentNotification(sendableNotification);
							}
						} else {
							log.trace("{} failed to write notification {}",
								apnsConnection.name, sendableNotification, writeFuture.cause());

							// Assume this is a temporary failure (we know it's not a permanent rejection because we didn't
							// even manage to write the notification to the wire) and re-enqueue for another send attempt.
							apnsConnection.listener.handleWriteFailure(apnsConnection, notification, writeFuture.cause());
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
	}

	/**
	 * <p>Waits for all pending read and write operations to finish. When this method exits normally (i.e. when it does
	 * not throw an {@code InterruptedException}), the following guarantees are made:</p>
	 *
	 * <ol>
	 * 	<li>All pending writes will have either finished successfully or been dispatched to this connection's listener
	 * 	via the {@link ApnsConnectionListener#handleWriteFailure(ApnsConnection, ApnsPushNotification, Throwable)}
	 * 	method.</li>
	 * 	<li>All pending reads will have completed, and rejected/unprocessed notifications will be dispatched to this
	 * 	connection's listener via the {@link ApnsConnectionListener#handleRejectedNotification(ApnsConnection, ApnsPushNotification, RejectedNotificationReason)}
	 * 	and {@link ApnsConnectionListener#handleUnprocessedNotifications(ApnsConnection, Collection)} methods.</li>
	 * </ol>
	 *
	 * <p>It is advisable for listeners to call this method when a connection is closed (though they must do so in a
	 * separate thread.</p>
	 *
	 * @throws InterruptedException if interrupted while waiting for pending read/write operations to finish
	 */
	public void waitForPendingOperationsToFinish() throws InterruptedException {
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
	 * @see ApnsConnectionListener#handleRejectedNotification(ApnsConnection, ApnsPushNotification, RejectedNotificationReason, java.util.Collection)
	 * @see ApnsConnectionListener#handleConnectionClosure(ApnsConnection)
	 */
	public synchronized void shutdownGracefully() {

		final ApnsConnection<T> apnsConnection = this;

		// We only need to send a known-bad notification if we were ever connected in the first place and if we're
		// still connected.
		if (this.handshakeCompleted && this.connectFuture.channel().isActive()) {

			this.connectFuture.channel().eventLoop().execute(new Runnable() {

				public void run() {
					// Don't send a second shutdown notification if we've already started the graceful shutdown process.
					if (apnsConnection.shutdownNotification == null) {

						log.debug("{} sending known-bad notification to shut down.", apnsConnection.name);

						apnsConnection.shutdownNotification = new SendableApnsPushNotification<KnownBadPushNotification>(
								new KnownBadPushNotification(), apnsConnection.sequenceNumber++);

						apnsConnection.pendingWriteCount += 1;

						apnsConnection.connectFuture.channel().writeAndFlush(apnsConnection.shutdownNotification).addListener(new GenericFutureListener<ChannelFuture>() {

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
	 * this method. This connection's listener will be notified when the connection has finished closing.</p>
	 *
	 * <p>Calling this method while not connected has no effect.</p>
	 *
	 * @see ApnsConnectionListener#handleConnectionClosure(ApnsConnection)
	 */
	public synchronized void shutdownImmediately() {
		if (this.connectFuture != null) {
			synchronized (this.connectFuture) {
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
