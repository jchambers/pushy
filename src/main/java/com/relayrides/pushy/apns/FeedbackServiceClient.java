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
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.Future;

import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>A client that communicates with the APNs feedback to retrieve expired device tokens. According to Apple's
 * documentation:</p>
 * 
 * <blockquote>The Apple Push Notification Service includes a feedback service to give you information about failed
 * push notifications. When a push notification cannot be delivered because the intended app does not exist on the
 * device, the feedback service adds that device's token to its list. Push notifications that expire before being
 * delivered are not considered a failed delivery and don't impact the feedback service...</blockquote>
 * 
 * <blockquote>Query the feedback service daily to get the list of device tokens. Use the timestamp to verify that the
 * device tokens haven't been reregistered since the feedback entry was generated. For each device that has not been
 * reregistered, stop sending notifications.</blockquote>
 * 
 * <p>Generally, users of Pushy should <em>not</em> instantiate a {@code FeedbackServiceClient} directly, but should
 * instead call {@link com.relayrides.pushy.apns.PushManager#getExpiredTokens()}, which will manage the creation
 * and configuration of a {@code FeedbackServiceClient} internally.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 * @see <a href="http://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/CommunicatingWIthAPS.html#//apple_ref/doc/uid/TP40008194-CH101-SW3">
 * Local and Push Notification Programming Guide - Provider Communication with Apple Push Notification Service - The
 * Feedback Service</a>
 */
class FeedbackServiceClient {

	private final PushManager<? extends ApnsPushNotification> pushManager;

	private Vector<ExpiredToken> expiredTokens;

	private final Logger log = LoggerFactory.getLogger(FeedbackServiceClient.class);

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

		private final FeedbackServiceClient feedbackClient;

		public FeedbackClientHandler(final FeedbackServiceClient feedbackClient) {
			this.feedbackClient = feedbackClient;
		}

		@Override
		protected void channelRead0(final ChannelHandlerContext context, final ExpiredToken expiredToken) {
			this.feedbackClient.addExpiredToken(expiredToken);
		}

		@Override
		public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {

			if (!(cause instanceof ReadTimeoutException)) {
				log.warn("Caught an unexpected exception while waiting for feedback.", cause);
			}

			context.close();
		}
	}

	/**
	 * <p>Constructs a new feedback client that connects to the feedback service in the given {@code PushManager}'s
	 * environment.</p>
	 * 
	 * @param pushManager the {@code PushManager} in whose environment this client should operate
	 */
	public FeedbackServiceClient(final PushManager<? extends ApnsPushNotification> pushManager) {
		this.pushManager = pushManager;
	}

	protected void addExpiredToken(final ExpiredToken expiredToken) {
		this.expiredTokens.add(expiredToken);
	}

	/**
	 * <p>Retrieves a list of expired tokens from the APNs feedback service. Be warned that this is a
	 * <strong>destructive operation</strong>. According to Apple's documentation:</p>
	 * 
	 * <blockquote>The feedback service's list is cleared after you read it. Each time you connect to the feedback
	 * service, the information it returns lists only the failures that have happened since you last
	 * connected.</blockquote>
	 * 
	 * @param timeout the time after the last received data after which the connection to the feedback service should
	 * be closed
	 * @param timeoutUnit the unit of time in which the given {@code timeout} is measured
	 * 
	 * @return a list of tokens that have expired since the last connection to the feedback service
	 * 
	 * @throws InterruptedException if interrupted while waiting for a response from the feedback service
	 */
	public synchronized List<ExpiredToken> getExpiredTokens(final long timeout, final TimeUnit timeoutUnit) throws InterruptedException {
		this.expiredTokens = new Vector<ExpiredToken>();

		final Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(pushManager.getWorkerGroup());
		bootstrap.channel(NioSocketChannel.class);

		final FeedbackServiceClient feedbackClient = this;
		bootstrap.handler(new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				final ChannelPipeline pipeline = channel.pipeline();

				if (pushManager.getEnvironment().isTlsRequired()) {
					pipeline.addLast("ssl", SslHandlerUtil.createSslHandler(pushManager.getKeyStore(), pushManager.getKeyStorePassword()));
				}

				pipeline.addLast("readTimeoutHandler", new ReadTimeoutHandler(timeout, timeoutUnit));
				pipeline.addLast("decoder", new ExpiredTokenDecoder());
				pipeline.addLast("handler", new FeedbackClientHandler(feedbackClient));
			}

		});

		final ChannelFuture connectFuture = bootstrap.connect(
				this.pushManager.getEnvironment().getFeedbackHost(),
				this.pushManager.getEnvironment().getFeedbackPort()).await();

		if (connectFuture.isSuccess()) {
			log.debug("Connected to feedback service.");

			if (this.pushManager.getEnvironment().isTlsRequired()) {
				final Future<Channel> handshakeFuture = connectFuture.channel().pipeline().get(SslHandler.class).handshakeFuture().await();

				if (handshakeFuture.isSuccess()) {
					log.debug("Completed TLS handshake with feedback service.");
					connectFuture.channel().closeFuture().await();
				} else if (handshakeFuture.cause() != null) {
					log.warn("Failed to complete TLS handshake with feedback service.", handshakeFuture.cause());
				} else if (handshakeFuture.isCancelled()) {
					log.debug("TLS handhsake attempt was cancelled.");
				}
			} else {
				connectFuture.channel().closeFuture().await();
			}
		} else if (connectFuture.cause() != null) {
			log.warn("Failed to connect to feedback service.", connectFuture.cause());
		} else if (connectFuture.isCancelled()) {
			log.debug("Attempt to connect to feedback service was cancelled.");
		}

		// The feedback service will send us a list of device tokens as soon as we connect, then hang up. While we're
		// waiting to sync with the connection closure, we'll be receiving messages from the feedback service from
		// another thread.
		return this.expiredTokens;
	}
}
