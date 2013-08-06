package com.relayrides.pushy;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

public class FeedbackClientInitializer extends ChannelInitializer<SocketChannel> {
	
	private final PushManager<? extends ApnsPushNotification> pushManager;
	private final FeedbackServiceClient feedbackClient;
	
	public FeedbackClientInitializer(final PushManager<? extends ApnsPushNotification> pushManager, final FeedbackServiceClient feedbackClient) {
		this.pushManager = pushManager;
		this.feedbackClient = feedbackClient;
	}
	
	@Override
	protected void initChannel(final SocketChannel channel) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException {
		
		final ChannelPipeline pipeline = channel.pipeline();
		
		if (this.pushManager.getEnvironment().isTlsRequired()) {
			pipeline.addLast("ssl", SslHandlerFactory.getSslHandler(
					this.pushManager.getKeyStore(), this.pushManager.getKeyStorePassword()));
		}
		
		pipeline.addLast("decoder", new ExpiredTokenDecoder());
		pipeline.addLast("handler", new FeedbackClientHandler(this.feedbackClient));
	}
}
