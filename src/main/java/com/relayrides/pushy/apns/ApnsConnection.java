package com.relayrides.pushy.apns;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ApnsConnection {
	private final ApnsEnvironment environment;

	private final String name;

	private final Object channelRegistrationMonitor = new Object();
	private ChannelFuture connectFuture;
	private volatile boolean handshakeCompleted = false;
	private volatile boolean closeOnRegistration;

	private static final Logger log = LoggerFactory.getLogger(ApnsConnection.class);

	public ApnsConnection(final ApnsEnvironment environment, final String name) {

		if (environment == null) {
			throw new NullPointerException("Environment must not be null.");
		}


		if (name == null) {
			throw new NullPointerException("Connection name must not be null.");
		}

		this.environment = environment;
		this.name = name;
	}

	protected abstract Bootstrap getBootstrap();

	public synchronized void connect() {
		if (this.connectFuture != null) {
			throw new IllegalStateException(String.format("%s already started a connection attempt.", this.name));
		}

		final ApnsConnection apnsConnection = this;

		log.debug("{} beginning connection process.", apnsConnection.name);
		this.connectFuture = this.getBootstrap().connect(this.getHost(), this.getPort());
		this.connectFuture.addListener(new GenericFutureListener<ChannelFuture>() {

			@Override
			public void operationComplete(final ChannelFuture connectFuture) {
				if (connectFuture.isSuccess()) {
					log.debug("{} connected; waiting for TLS handshake.", apnsConnection.name);

					final SslHandler sslHandler = connectFuture.channel().pipeline().get(SslHandler.class);

					try {
						sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<Channel>>() {

							@Override
							public void operationComplete(final Future<Channel> handshakeFuture) {
								if (handshakeFuture.isSuccess()) {
									log.debug("{} successfully completed TLS handshake.", apnsConnection.name);

									apnsConnection.handshakeCompleted = true;
									apnsConnection.handleConnectionCompletion(connectFuture.channel());

									final ApnsConnectionListener listener = apnsConnection.getListener();

									if (listener != null) {
										listener.handleConnectionSuccess(apnsConnection);
									}

								} else {
									log.debug("{} failed to complete TLS handshake with APNs gateway.",
											apnsConnection.name, handshakeFuture.cause());

									connectFuture.channel().close();

									final ApnsConnectionListener listener = apnsConnection.getListener();

									if (listener != null) {
										listener.handleConnectionFailure(apnsConnection, handshakeFuture.cause());
									}
								}
							}});
					} catch (NullPointerException e) {
						log.warn("{} failed to get SSL handler and could not wait for a TLS handshake.", apnsConnection.name);

						connectFuture.channel().close();

						final ApnsConnectionListener listener = apnsConnection.getListener();

						if (listener != null) {
							listener.handleConnectionFailure(apnsConnection, e);
						}
					}
				} else {
					log.debug("{} failed to connect to APNs gateway.", apnsConnection.name, connectFuture.cause());

					final ApnsConnectionListener listener = apnsConnection.getListener();

					if (listener != null) {
						listener.handleConnectionFailure(apnsConnection, connectFuture.cause());
					}
				}
			}
		});
	}

	protected void handleConnectionCompletion(final Channel channel) {}

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

	protected boolean shouldCloseOnRegistration() {
		return this.closeOnRegistration;
	}

	protected boolean hasCompletedHandshake() {
		return this.handshakeCompleted;
	}

	protected Object getChannelRegistrationMonitor() {
		return this.channelRegistrationMonitor;
	}

	protected Channel getChannel() {
		return this.connectFuture != null ? this.connectFuture.channel() : null;
	}

	public String getName() {
		return this.name;
	}

	public ApnsEnvironment getEnvironment() {
		return this.environment;
	}

	protected abstract String getHost();
	protected abstract int getPort();

	public abstract ApnsConnectionListener getListener();
}
