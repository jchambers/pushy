package com.relayrides.pushy.apns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import org.slf4j.Logger;

public abstract class ApnsConnection {
	private final ApnsEnvironment environment;
	private final NioEventLoopGroup eventLoopGroup;
	private final ApnsConnectionConfiguration configuration;

	private final ApnsConnectionListener listener;

	private final String name;

	private final Object channelRegistrationMonitor = new Object();
	private ChannelFuture connectFuture;
	private volatile boolean handshakeCompleted = false;
	private volatile boolean closeOnRegistration;

	public ApnsConnection(final ApnsEnvironment environment, final NioEventLoopGroup eventLoopGroup,
			final ApnsConnectionConfiguration configuration, final ApnsConnectionListener listener, final String name) {

		if (environment == null) {
			throw new NullPointerException("Environment must not be null.");
		}

		if (eventLoopGroup == null) {
			throw new NullPointerException("Event loop group must not be null.");
		}

		if (configuration == null) {
			throw new NullPointerException("Connection configuration must not be null.");
		}

		if (name == null) {
			throw new NullPointerException("Connection name must not be null.");
		}

		this.environment = environment;
		this.eventLoopGroup = eventLoopGroup;
		this.configuration = configuration;
		this.listener = listener;
		this.name = name;
	}

	public synchronized void connect() {
		if (this.connectFuture != null) {
			throw new IllegalStateException(String.format("%s already started a connection attempt.", this.name));
		}

		final ApnsConnection apnsConnection = this;

		this.getLogger().debug("{} beginning connection process.", apnsConnection.name);
		this.connectFuture = this.getBootstrap().connect(this.environment.getApnsGatewayHost(), this.environment.getApnsGatewayPort());
		this.connectFuture.addListener(new GenericFutureListener<ChannelFuture>() {

			@Override
			public void operationComplete(final ChannelFuture connectFuture) {
				if (connectFuture.isSuccess()) {
					apnsConnection.getLogger().debug("{} connected; waiting for TLS handshake.", apnsConnection.name);

					final SslHandler sslHandler = connectFuture.channel().pipeline().get(SslHandler.class);

					try {
						sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<Channel>>() {

							@Override
							public void operationComplete(final Future<Channel> handshakeFuture) {
								if (handshakeFuture.isSuccess()) {
									apnsConnection.getLogger().debug("{} successfully completed TLS handshake.", apnsConnection.name);

									apnsConnection.handshakeCompleted = true;

									apnsConnection.handleConnectionCompletion(connectFuture.channel());

									if (apnsConnection.listener != null) {
										apnsConnection.listener.handleConnectionSuccess(apnsConnection);
									}

								} else {
									apnsConnection.getLogger().debug("{} failed to complete TLS handshake with APNs gateway.",
											apnsConnection.name, handshakeFuture.cause());

									connectFuture.channel().close();

									if (apnsConnection.listener != null) {
										apnsConnection.listener.handleConnectionFailure(apnsConnection, handshakeFuture.cause());
									}
								}
							}});
					} catch (NullPointerException e) {
						apnsConnection.getLogger().warn("{} failed to get SSL handler and could not wait for a TLS handshake.", apnsConnection.name);

						connectFuture.channel().close();

						if (apnsConnection.listener != null) {
							apnsConnection.listener.handleConnectionFailure(apnsConnection, e);
						}
					}
				} else {
					apnsConnection.getLogger().debug("{} failed to connect to APNs gateway.", apnsConnection.name, connectFuture.cause());

					if (apnsConnection.listener != null) {
						apnsConnection.listener.handleConnectionFailure(apnsConnection, connectFuture.cause());
					}
				}
			}
		});
	}

	/**
	 * <p>Immediately closes this connection (assuming it was ever open). If the connection was previously open, the
	 * connection's listener will be notified of the connection's closure. If a connection attempt was in progress, the
	 * listener will be notified of a connection failure. If the connection was never open, this method has no effect.</p>
	 *
	 * @see ApnsConnectionListener#handleConnectionClosure(ApnsConnection)
	 * @see ApnsConnectionListener#handleConnectionFailure(ApnsConnection, Throwable)
	 */
	public synchronized void shutdownImmediately() {
		if (this.connectFuture != null) {
			synchronized (this.channelRegistrationMonitor) {
				if (this.connectFuture.channel().isRegistered()) {
					this.connectFuture.channel().eventLoop().execute(this.getImmediateShutdownRunnable());
				} else {
					this.closeOnRegistration = true;
				}
			}
		}
	}

	protected Runnable getImmediateShutdownRunnable() {
		final ApnsConnection apnsConnection = this;

		return new Runnable() {
			@Override
			public void run() {
				final SslHandler sslHandler = apnsConnection.connectFuture.channel().pipeline().get(SslHandler.class);

				if (apnsConnection.connectFuture.isCancellable()) {
					apnsConnection.connectFuture.cancel(true);
				} else if (sslHandler != null && sslHandler.handshakeFuture().isCancellable()) {
					sslHandler.handshakeFuture().cancel(true);
				} else {
					apnsConnection.connectFuture.channel().close();
				}
			}
		};
	}
	protected abstract void handleConnectionCompletion(Channel channel);
	protected abstract Logger getLogger();

	protected Bootstrap getBootstrap() {
		final Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(this.eventLoopGroup);
		bootstrap.channel(NioSocketChannel.class);
		bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);

		return bootstrap;
	}

	protected boolean shouldCloseOnRegistration() {
		return this.closeOnRegistration;
	}

	protected boolean hasCompletedHandshake() {
		return this.handshakeCompleted;
	}

	protected Object getChannelRegistrationMonitor() {
		return this.channelRegistrationMonitor;
	}

	public ApnsConnectionListener getListener() {
		return this.listener;
	}

	public String getName() {
		return this.name;
	}
}
