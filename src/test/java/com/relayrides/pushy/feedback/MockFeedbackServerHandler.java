package com.relayrides.pushy.feedback;

import java.util.List;

import com.relayrides.pushy.feedback.TokenExpiration;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.GenericFutureListener;

public class MockFeedbackServerHandler extends ChannelInboundHandlerAdapter {
	
	private final MockFeedbackServer feedbackServer;
	
	public MockFeedbackServerHandler(final MockFeedbackServer feedbackServer) {
		this.feedbackServer = feedbackServer;
	}
	
	@Override
    public void channelActive(final ChannelHandlerContext context) {
		
		ChannelFuture lastWriteFuture = null;
		
		final List<TokenExpiration> expiredTokens = this.feedbackServer.getAndClearAllExpiredTokens();
		
		for (final TokenExpiration expiredToken : expiredTokens) {
			lastWriteFuture = context.writeAndFlush(expiredToken);
		}
		
		if (lastWriteFuture != null) {
			lastWriteFuture.addListener(new GenericFutureListener<ChannelFuture>() {

				public void operationComplete(final ChannelFuture future) throws Exception {
					context.close();
				}
				
			});
		} else {
			context.close();
		}
	}
}
