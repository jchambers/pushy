package com.relayrides.pushy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class FeedbackClientHandler extends SimpleChannelInboundHandler<TokenExpiration> {

	private final FeedbackServiceClient feedbackClient;
	
	public FeedbackClientHandler(final FeedbackServiceClient feedbackClient) {
		this.feedbackClient = feedbackClient;
	}
	
	@Override
	protected void channelRead0(final ChannelHandlerContext context, final TokenExpiration expiredToken) {
		this.feedbackClient.addExpiredToken(expiredToken);
	}
}
