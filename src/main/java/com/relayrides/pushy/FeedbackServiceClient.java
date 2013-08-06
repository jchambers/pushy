package com.relayrides.pushy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class FeedbackServiceClient {
	
	private final ApnsEnvironment environment;
	
	private final Bootstrap bootstrap;
	private final Vector<TokenExpiration> expiredTokens;
	
	protected FeedbackServiceClient(final ApnsEnvironment environment) {
		this(environment, null, null);
	}
	
	protected FeedbackServiceClient(final ApnsEnvironment environment, final KeyStore keyStore, final char[] keyStorePassword) {
		
		if (environment.isTlsRequired() && keyStore == null) {
			throw new IllegalArgumentException("Must pass a KeyStore and password for environments that require TLS.");
		}

		this.environment = environment;
		
		this.bootstrap = new Bootstrap();
		this.bootstrap.group(new NioEventLoopGroup());
		this.bootstrap.channel(NioSocketChannel.class);
		
		final FeedbackServiceClient feedbackClient = this;
		this.bootstrap.handler(new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				final ChannelPipeline pipeline = channel.pipeline();
				
				if (environment.isTlsRequired()) {
					pipeline.addLast("ssl", SslHandlerFactory.getSslHandler(keyStore, keyStorePassword));
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
	
	protected void destroy() throws InterruptedException {
		this.bootstrap.group().shutdownGracefully().sync();
	}
}
