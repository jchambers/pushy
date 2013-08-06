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

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class FeedbackServiceClient {
	
	private final PushManager<? extends ApnsPushNotification> pushManager;
	
	private final Bootstrap bootstrap;
	private Vector<ExpiredToken> expiredTokens;
	
	public FeedbackServiceClient(final PushManager<? extends ApnsPushNotification> pushManager) {
		
		this.pushManager = pushManager;
		
		this.bootstrap = new Bootstrap();
		this.bootstrap.group(new NioEventLoopGroup());
		this.bootstrap.channel(NioSocketChannel.class);
		
		final FeedbackServiceClient feedbackClient = this;
		this.bootstrap.handler(new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				final ChannelPipeline pipeline = channel.pipeline();
				
				if (pushManager.getEnvironment().isTlsRequired()) {
					pipeline.addLast("ssl", SslHandlerFactory.getSslHandler(pushManager.getKeyStore(), pushManager.getKeyStorePassword()));
				}
				
				pipeline.addLast("decoder", new ExpiredTokenDecoder());
				pipeline.addLast("handler", new FeedbackClientHandler(feedbackClient));
			}
			
		});
	}
	
	protected void addExpiredToken(final ExpiredToken expiredToken) {
		this.expiredTokens.add(expiredToken);
	}
	
	public synchronized List<ExpiredToken> getExpiredTokens() throws InterruptedException {
		this.expiredTokens = new Vector<ExpiredToken>();
		final ArrayList<ExpiredToken> returnedExpiredTokens = new ArrayList<ExpiredToken>(this.expiredTokens);
		
		try {
			final ChannelFuture connectFuture = this.bootstrap.connect(this.pushManager.getEnvironment().getFeedbackHost(), this.pushManager.getEnvironment().getFeedbackPort()).sync();
			
			if (connectFuture.isSuccess()) {
				final Future<Channel> handshakeFuture = connectFuture.channel().pipeline().get(SslHandler.class).handshakeFuture().sync();
				
				if (handshakeFuture.isSuccess()) {
					connectFuture.channel().closeFuture().sync();
					
					returnedExpiredTokens.addAll(this.expiredTokens);
				}
			}
			
			this.expiredTokens.clear();
			return returnedExpiredTokens;
		} finally {
			this.bootstrap.group().shutdownGracefully();
		}
	}
}
