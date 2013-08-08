package com.relayrides.pushy.feedback;

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

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import com.relayrides.pushy.apns.ApnsEnvironment;
import com.relayrides.pushy.apns.SslCapableChannelInitializer;

public class FeedbackServiceClient {
	
	private final ApnsEnvironment environment;
	
	private final Bootstrap bootstrap;
	private final Vector<TokenExpiration> expiredTokens;
	
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
					out.add(new TokenExpiration(this.token, this.expiration));
					
					this.checkpoint(ExpiredTokenDecoderState.EXPIRATION);
					
					break;
				}
			}
		}
	}
	
	private class FeedbackClientHandler extends SimpleChannelInboundHandler<TokenExpiration> {

		private final FeedbackServiceClient feedbackClient;
		
		public FeedbackClientHandler(final FeedbackServiceClient feedbackClient) {
			this.feedbackClient = feedbackClient;
		}
		
		@Override
		protected void channelRead0(final ChannelHandlerContext context, final TokenExpiration expiredToken) {
			this.feedbackClient.addExpiredToken(expiredToken);
		}
	}
	
	public FeedbackServiceClient(final ApnsEnvironment environment) {
		this(environment, null, null);
	}
	
	public FeedbackServiceClient(final ApnsEnvironment environment, final KeyStore keyStore, final char[] keyStorePassword) {
		
		if (environment.isTlsRequired() && keyStore == null) {
			throw new IllegalArgumentException("Must pass a KeyStore for environments that require TLS.");
		}

		this.environment = environment;
		
		this.bootstrap = new Bootstrap();
		this.bootstrap.group(new NioEventLoopGroup());
		this.bootstrap.channel(NioSocketChannel.class);
		
		final FeedbackServiceClient feedbackClient = this;
		this.bootstrap.handler(new SslCapableChannelInitializer() {

			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				final ChannelPipeline pipeline = channel.pipeline();
				
				if (environment.isTlsRequired()) {
					pipeline.addLast("ssl", this.getSslHandler(keyStore, keyStorePassword));
				}
				
				pipeline.addLast("decoder", new ExpiredTokenDecoder());
				pipeline.addLast("handler", new FeedbackClientHandler(feedbackClient));
			}
			
		});
		
		this.expiredTokens = new Vector<TokenExpiration>();
	}
	
	protected void addExpiredToken(final TokenExpiration expiredToken) {
		this.expiredTokens.add(expiredToken);
	}
	
	public synchronized List<TokenExpiration> getExpiredTokens() throws InterruptedException {
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
		
		return new ArrayList<TokenExpiration>(this.expiredTokens);
	}
	
	public void destroy() throws InterruptedException {
		this.bootstrap.group().shutdownGracefully().sync();
	}
}
