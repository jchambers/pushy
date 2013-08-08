package com.relayrides.pushy.apns;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.GenericFutureListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.relayrides.pushy.apns.RejectedNotificationException;
import com.relayrides.pushy.util.SimpleApnsPushNotification;

public class MockApnsServerHandler extends SimpleChannelInboundHandler<ReceivedApnsPushNotification<SimpleApnsPushNotification>> {

	private final MockApnsServer server;
	private final Logger log = LoggerFactory.getLogger(MockApnsServerHandler.class);
	
	private boolean rejectFutureMessages = false;
	
	public MockApnsServerHandler(final MockApnsServer server) {
		this.server = server;
	}
	
	@Override
	protected void channelRead0(final ChannelHandlerContext context, ReceivedApnsPushNotification<SimpleApnsPushNotification> receivedNotification) throws Exception {
		
		if (!this.rejectFutureMessages) {
			final RejectedNotificationException exception = this.server.handleReceivedNotification(receivedNotification);
			
			if (exception != null) {
				
				this.rejectFutureMessages = true;
				
				context.writeAndFlush(exception).addListener(new GenericFutureListener<ChannelFuture>() {
	
					public void operationComplete(final ChannelFuture future) {
						context.close();
					}
					
				});
			}
		}
	}
	
	@Override
	public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
		log.error("Mock APNS server caught an exception.", cause);
	}
}
