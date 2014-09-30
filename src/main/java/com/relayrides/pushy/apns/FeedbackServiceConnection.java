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
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.Date;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>A connection to the APNs feedback service that listens for expired tokens, then disconnects after a period of
 * inactivity. According to Apple's documentation:</p>
 *
 * <blockquote><p>The Apple Push Notification Service includes a feedback service to give you information about failed
 * push notifications. When a push notification cannot be delivered because the intended app does not exist on the
 * device, the feedback service adds that device's token to its list. Push notifications that expire before being
 * delivered are not considered a failed delivery and don't impact the feedback service...</p>
 *
 * <p>Query the feedback service daily to get the list of device tokens. Use the timestamp to verify that the device
 * tokens haven't been reregistered since the feedback entry was generated. For each device that has not been
 * reregistered, stop sending notifications.</p></blockquote>
 *
 * <p>Generally, users of Pushy should <em>not</em> instantiate a {@code FeedbackServiceConnection} directly, but should
 * instead call {@link com.relayrides.pushy.apns.PushManager#requestExpiredTokens()}, which will manage the creation and
 * configuration of a {@code FeedbackServiceConnection} internally.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 * @see <a href="http://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/CommunicatingWIthAPS.html#//apple_ref/doc/uid/TP40008194-CH101-SW3">
 * Local and Push Notification Programming Guide - Provider Communication with Apple Push Notification Service - The
 * Feedback Service</a>
 */
public class FeedbackServiceConnection {

	private final ApnsEnvironment environment;
	private final SSLContext sslContext;
	private final NioEventLoopGroup eventLoopGroup;
	private final FeedbackConnectionConfiguration configuration;
	private final FeedbackServiceListener listener;
	private final String name;

	private final Object channelRegistrationMonitor = new Object();
	private ChannelFuture connectFuture;
	private volatile boolean handshakeCompleted = false;
	private volatile boolean closeOnRegistration;

	private static final Logger log = LoggerFactory.getLogger(FeedbackServiceConnection.class);

	private enum ExpiredTokenDecoderState {
		EXPIRATION,
		TOKEN_LENGTH,
		TOKEN
	}

	private class ExpiredTokenDecoder extends ReplayingDecoder<ExpiredTokenDecoderState> {

		private Date expiration;
		private byte[] token;

		public ExpiredTokenDecoder() {
			super(ExpiredTokenDecoderState.EXPIRATION);
		}

		@Override
		protected void decode(final ChannelHandlerContext context, final ByteBuf in, final List<Object> out) {
			switch (this.state()) {
				case EXPIRATION: {
					final long timestamp = (in.readInt() & 0xFFFFFFFFL) * 1000L;
					this.expiration = new Date(timestamp);

					this.checkpoint(ExpiredTokenDecoderState.TOKEN_LENGTH);

					break;
				}

				case TOKEN_LENGTH: {
					this.token = new byte[in.readShort() & 0x0000FFFF];
					this.checkpoint(ExpiredTokenDecoderState.TOKEN);

					break;
				}

				case TOKEN: {
					in.readBytes(this.token);
					out.add(new ExpiredToken(this.token, this.expiration));

					this.checkpoint(ExpiredTokenDecoderState.EXPIRATION);

					break;
				}
			}
		}
	}

	private class FeedbackClientHandler extends SimpleChannelInboundHandler<ExpiredToken> {

		private final FeedbackServiceConnection feedbackClient;

		public FeedbackClientHandler(final FeedbackServiceConnection feedbackClient) {
			this.feedbackClient = feedbackClient;
		}

		@Override
		public void channelRegistered(final ChannelHandlerContext context) throws Exception {
			super.channelRegistered(context);

			synchronized (this.feedbackClient.channelRegistrationMonitor) {
				if (this.feedbackClient.closeOnRegistration) {
					log.debug("Channel registered for {}, but shutting down immediately.", this.feedbackClient.name);
					context.channel().eventLoop().execute(this.feedbackClient.getImmediateShutdownRunnable());
				}
			}
		}

		@Override
		protected void channelRead0(final ChannelHandlerContext context, final ExpiredToken expiredToken) {
			if (this.feedbackClient.listener != null) {
				this.feedbackClient.listener.handleExpiredToken(feedbackClient, expiredToken);
			}
		}

		@Override
		public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {

			if (!(cause instanceof ReadTimeoutException)) {
				log.debug("Caught an unexpected exception while waiting for expired tokens.", cause);
			}

			context.close();
		}

		@Override
		public void channelInactive(final ChannelHandlerContext context) throws Exception {
			super.channelInactive(context);

			// Channel closure implies that the connection attempt had fully succeeded, so we only want to notify
			// listeners if the handshake has completed. Otherwise, we'll notify listeners of a connection failure (as
			// opposed to closure) elsewhere.
			if (this.feedbackClient.handshakeCompleted) {
				if (this.feedbackClient.listener != null) {
					this.feedbackClient.listener.handleConnectionClosure(this.feedbackClient);
				}
			}
		}
	}

	/**
	 * <p>Constructs a new feedback client that connects to the feedback service in the given environment with the
	 * credentials and key/trust managers in the given SSL context.</p>

	 * @param environment the environment in which this feedback client will operate
	 * @param sslContext an SSL context with the keys/certificates and trust managers this client should use when
	 * communicating with the APNs feedback service
	 * @param eventLoopGroup the event loop group this client should use for asynchronous network operations
	 * @param configuration the set of configuration options to use for this connection. The configuration object is
	 * copied and changes to the original object will not propagate to the connection after creation. Must not be
	 * {@code null}.
	 * @param name a human-readable name for this connection; names must not be {@code null}
	 */
	public FeedbackServiceConnection(final ApnsEnvironment environment, final SSLContext sslContext, final NioEventLoopGroup eventLoopGroup, final FeedbackConnectionConfiguration configuration, final FeedbackServiceListener listener, final String name) {
		if (environment == null) {
			throw new NullPointerException("Environment must not be null.");
		}

		if (sslContext == null) {
			throw new NullPointerException("SSL context must not be null.");
		}

		if (eventLoopGroup == null) {
			throw new NullPointerException("Event loop group must not be null.");
		}

		if (configuration == null) {
			throw new NullPointerException("Feedback service connection configuration must not be null.");
		}

		if (name == null) {
			throw new NullPointerException("Feedback service connection name must not be null.");
		}

		this.environment = environment;
		this.sslContext = sslContext;
		this.eventLoopGroup = eventLoopGroup;
		this.configuration = configuration;
		this.listener = listener;
		this.name = name;
	}

	/**
	 * <p>Connects to the APNs feedback service and waits for expired tokens to arrive. Be warned that this is a
	 * <strong>destructive operation</strong>. According to Apple's documentation:</p>
	 *
	 * <blockquote>The feedback service's list is cleared after you read it. Each time you connect to the feedback
	 * service, the information it returns lists only the failures that have happened since you last
	 * connected.</blockquote>
	 */
	public synchronized void connect() {

		if (this.connectFuture != null) {
			throw new IllegalStateException(String.format("%s already started a connection attempt.", this.name));
		}

		final Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(this.eventLoopGroup);
		bootstrap.channel(NioSocketChannel.class);

		final FeedbackServiceConnection feedbackConnection = this;
		bootstrap.handler(new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				final ChannelPipeline pipeline = channel.pipeline();

				final SSLEngine sslEngine = feedbackConnection.sslContext.createSSLEngine();
				sslEngine.setUseClientMode(true);

				pipeline.addLast("ssl", new SslHandler(sslEngine));
				pipeline.addLast("readTimeoutHandler", new ReadTimeoutHandler(feedbackConnection.configuration.getReadTimeout()));
				pipeline.addLast("decoder", new ExpiredTokenDecoder());
				pipeline.addLast("handler", new FeedbackClientHandler(feedbackConnection));
			}
		});

		this.connectFuture = bootstrap.connect(this.environment.getFeedbackHost(), this.environment.getFeedbackPort());
		this.connectFuture.addListener(new GenericFutureListener<ChannelFuture>() {

			@Override
			public void operationComplete(final ChannelFuture connectFuture) {

				if (connectFuture.isSuccess()) {
					log.debug("{} connected; waiting for TLS handshake.", feedbackConnection.name);

					final SslHandler sslHandler = connectFuture.channel().pipeline().get(SslHandler.class);

					try {
						sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<Channel>>() {

							@Override
							public void operationComplete(final Future<Channel> handshakeFuture) {
								if (handshakeFuture.isSuccess()) {
									log.debug("{} successfully completed TLS handshake.", feedbackConnection.name);

									feedbackConnection.handshakeCompleted = true;

									if (feedbackConnection.listener != null) {
										feedbackConnection.listener.handleConnectionSuccess(feedbackConnection);
									}

								} else {
									log.debug("{} failed to complete TLS handshake with APNs feedback service.",
											feedbackConnection.name, handshakeFuture.cause());

									connectFuture.channel().close();

									if (feedbackConnection.listener != null) {
										feedbackConnection.listener.handleConnectionFailure(feedbackConnection, handshakeFuture.cause());
									}
								}
							}});
					} catch (NullPointerException e) {
						log.warn("{} failed to get SSL handler and could not wait for a TLS handshake.", feedbackConnection.name);

						connectFuture.channel().close();

						if (feedbackConnection.listener != null) {
							feedbackConnection.listener.handleConnectionFailure(feedbackConnection, e);
						}
					}
				} else {
					log.debug("{} failed to connect to APNs feedback service.", feedbackConnection.name, connectFuture.cause());

					if (feedbackConnection.listener != null) {
						feedbackConnection.listener.handleConnectionFailure(feedbackConnection, connectFuture.cause());
					}
				}
			}
		});
	}

	/**
	 * Closes this feedback connection as soon as possible. Calling this method when the feedback connection is not
	 * connected has no effect.
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
		final FeedbackServiceConnection feedbackConnection = this;

		return new Runnable() {
			@Override
			public void run() {
				final SslHandler sslHandler = feedbackConnection.connectFuture.channel().pipeline().get(SslHandler.class);

				if (feedbackConnection.connectFuture.isCancellable()) {
					feedbackConnection.connectFuture.cancel(true);
				} else if (sslHandler != null && sslHandler.handshakeFuture().isCancellable()) {
					sslHandler.handshakeFuture().cancel(true);
				} else {
					feedbackConnection.connectFuture.channel().close();
				}
			}
		};
	}

	@Override
	public String toString() {
		return "FeedbackServiceConnection [name=" + name + "]";
	}
}
