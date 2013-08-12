package com.relayrides.pushy.apns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

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
public class FeedbackServiceClient {
	
	private final ApnsEnvironment environment;
	
	private final Bootstrap bootstrap;
	private final Vector<ExpiredToken> expiredTokens;
	
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
	}
	
	/**
	 * <p>Constructs a new feedback client that connects to the feedback service in the given {@code PushManager}'s
	 * environment.</p>
	 * 
	 * @param pushManager the {@code PushManager} in whose environment this client should operate
	 */
	public FeedbackServiceClient(final PushManager<? extends ApnsPushNotification> pushManager) {
		
		this.environment = pushManager.getEnvironment();
		
		this.bootstrap = new Bootstrap();
		this.bootstrap.group(new NioEventLoopGroup());
		this.bootstrap.channel(NioSocketChannel.class);
		
		final FeedbackServiceClient feedbackClient = this;
		this.bootstrap.handler(new SslCapableChannelInitializer() {

			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				final ChannelPipeline pipeline = channel.pipeline();
				
				if (environment.isTlsRequired()) {
					pipeline.addLast("ssl", this.getSslHandler(pushManager.getKeyStore(), pushManager.getKeyStorePassword()));
				}
				
				pipeline.addLast("decoder", new ExpiredTokenDecoder());
				pipeline.addLast("handler", new FeedbackClientHandler(feedbackClient));
			}
			
		});
		
		this.expiredTokens = new Vector<ExpiredToken>();
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
	 * @return a list of tokens that have expired since the last connection to the feedback service
	 * 
	 * @throws InterruptedException if interrupted while waiting for a response from the feedback service
	 */
	public synchronized List<ExpiredToken> getExpiredTokens() throws InterruptedException {
		this.expiredTokens.clear();
		
		final ChannelFuture connectFuture =
				this.bootstrap.connect(this.environment.getFeedbackHost(), this.environment.getFeedbackPort()).sync();
		
		if (connectFuture.isSuccess()) {
			if (this.environment.isTlsRequired()) {
				final Future<Channel> handshakeFuture = connectFuture.channel().pipeline().get(SslHandler.class).handshakeFuture().sync();
				
				if (handshakeFuture.isSuccess()) {
					connectFuture.channel().closeFuture().sync();
				}
			} else {
				connectFuture.channel().closeFuture().sync();
			}
		}
		
		// The feedback service will send us a list of device tokens as soon as we connect, then hang up. While we're
		// waiting to sync with the connection closure, we'll be receiving messages from the feedback service from
		// another thread.
		return new ArrayList<ExpiredToken>(this.expiredTokens);
	}
	
	public void destroy() throws InterruptedException {
		this.bootstrap.group().shutdownGracefully().sync();
	}
}
