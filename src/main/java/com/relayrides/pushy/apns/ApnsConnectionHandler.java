package com.relayrides.pushy.apns;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class ApnsConnectionHandler<I> extends SimpleChannelInboundHandler<I> {

	private final ApnsConnection apnsConnection;

	private static final Logger log = LoggerFactory.getLogger(ApnsConnectionHandler.class);

	public ApnsConnectionHandler(final ApnsConnection apnsConnection) {
		this.apnsConnection = apnsConnection;
	}

	@Override
	public void channelRegistered(final ChannelHandlerContext context) throws Exception {
		super.channelRegistered(context);

		synchronized (this.apnsConnection.getChannelRegistrationMonitor()) {
			if (this.apnsConnection.shouldCloseOnRegistration()) {
				log.debug("Channel registered for {}, but shutting down immediately.", this.apnsConnection.getName());
				context.channel().eventLoop().execute(this.apnsConnection.getImmediateShutdownRunnable());
			}
		}
	}

	@Override
	public void channelInactive(final ChannelHandlerContext context) throws Exception {
		super.channelInactive(context);

		// Channel closure implies that the connection attempt had fully succeeded, so we only want to notify
		// listeners if the handshake has completed. Otherwise, we'll notify listeners of a connection failure (as
		// opposed to closure) elsewhere.
		if (this.apnsConnection.hasCompletedHandshake()) {
			if (this.apnsConnection.getListener() != null) {
				this.apnsConnection.getListener().handleConnectionClosure(this.apnsConnection);
			}
		}
	}
}
