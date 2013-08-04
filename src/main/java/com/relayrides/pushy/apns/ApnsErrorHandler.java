package com.relayrides.pushy.apns;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ApnsErrorHandler extends SimpleChannelInboundHandler<ApnsException> {

	@Override
	protected void channelRead0(final ChannelHandlerContext context, final ApnsException e) throws Exception {
		// TODO Actually do something reasonable here
		System.err.println(String.format("APNS server reported error of type %s for notification ID %d.",
				e.getErrorCode(), e.getNotificationId()));
	}
}
