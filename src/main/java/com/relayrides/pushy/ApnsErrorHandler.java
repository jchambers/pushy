package com.relayrides.pushy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ApnsErrorHandler<T extends ApnsPushNotification> extends SimpleChannelInboundHandler<ApnsException> {

	private final PushManager<T> pushManager;
	private final ApnsClientThread<T> clientThread;
	
	public ApnsErrorHandler(final PushManager<T> pushManager, final ApnsClientThread<T> clientThread) {
		this.pushManager = pushManager;
		this.clientThread = clientThread;
	}
	
	@Override
	protected void channelRead0(final ChannelHandlerContext context, final ApnsException e) throws Exception {
		this.clientThread.reconnect();
		
		final T failedNotification =
				this.clientThread.getSentNotificationBuffer().getFailedNotificationAndClearPriorNotifications(e.getNotificationId());
		
		this.pushManager.handleFailedDelivery(failedNotification, e.getErrorCode());
		this.pushManager.enqueueAllNotifications(this.clientThread.getSentNotificationBuffer().getAllNotifications());
	}
}
