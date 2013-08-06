package com.relayrides.pushy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ApnsErrorHandler<T extends ApnsPushNotification> extends SimpleChannelInboundHandler<ApnsException> {

	private final PushManager<T> pushManager;
	private final ApnsClientThread<T> clientThread;
	
	private final Logger log = LoggerFactory.getLogger(ApnsErrorHandler.class);
	
	public ApnsErrorHandler(final PushManager<T> pushManager, final ApnsClientThread<T> clientThread) {
		this.pushManager = pushManager;
		this.clientThread = clientThread;
	}
	
	@Override
	protected void channelRead0(final ChannelHandlerContext context, final ApnsException e) throws Exception {
		this.clientThread.reconnect();
		
		final T failedNotification =
				this.clientThread.getSentNotificationBuffer().getFailedNotificationAndClearBuffer(e.getNotificationId(), pushManager);
		
		if (e.getErrorCode() != ApnsErrorCode.SHUTDOWN) {
			this.pushManager.notifyListenersOfFailedDelivery(failedNotification, e);
		}
	}
	
	@Override
	public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
		this.clientThread.reconnect();
		
		log.debug("Caught an exception; reconnecting.", cause);
	}
}
