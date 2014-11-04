/* Copyright (c) 2014 RelayRides
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.relayrides.pushy.apns;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract base class for connections to the various services in an APNs connection. All connections to APNs
 * services use TLS, and this base class provides functionality common to all connections for establishing and
 * closing connections.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public abstract class ApnsConnection {
	private final String name;

	private final Object channelRegistrationMonitor = new Object();
	private ChannelFuture connectFuture;
	private volatile boolean closeOnRegistration;

	private static final Logger log = LoggerFactory.getLogger(ApnsConnection.class);

	public static abstract class ApnsConnectionHandler<I> extends SimpleChannelInboundHandler<I> {

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

	/**
	 * Constructs a new connection with the given environment and name.
	 *
	 * @param name a human-readable name for this connection; names must not be {@code null}
	 */
	public ApnsConnection(final String name) {
		if (name == null) {
			throw new NullPointerException("Connection name must not be null.");
		}

		this.name = name;
	}

	/**
	 * Returns a Netty {@link Bootstrap} instance to be used to configure this connection and its pipeline. The returned
	 * bootstrap instance must have a handler that initializes the channel with a pipeline that contains an SSL handler.
	 *
	 * @return a bootstrap instance to be used to configure this connection
	 */
	protected abstract Bootstrap getBootstrap();

	/**
	 * Asynchronously opens a connection and performs a TLS handshake with a service in the APNs environment. If
	 * provided, the connection's listener is notified when the connection attempt is completed or when it fails.
	 *
	 * @see com.relayrides.pushy.apns.ApnsConnection#getListener()
	 */
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

	/**
	 * Performs optional additional setup operations after the connection has completed a TLS handshake with the APNs
	 * server. This method is called before this connection's listener (if any) is notified of connection success. This
	 * method must not block.
	 *
	 * @param channel the channel associated with this connection
	 */
	protected abstract void handleConnectionCompletion(Channel channel);

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

	private Runnable getImmediateShutdownRunnable() {
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

	private boolean shouldCloseOnRegistration() {
		return this.closeOnRegistration;
	}

	private Object getChannelRegistrationMonitor() {
		return this.channelRegistrationMonitor;
	}

	/**
	 * Indicates whether this connection has completed its TLS handshake with an APNs server.
	 *
	 * @return {@code true} if this connection has successfully completed a TLS handshake with an APNs server or
	 * {@code false} otherwise
	 */
	protected boolean hasCompletedHandshake() {
		try {
			return this.connectFuture.channel().pipeline().get(SslHandler.class).handshakeFuture().isSuccess();
		} catch (NullPointerException e) {
			// Either we don't have a connect future yet or we couldn't get the SslHandler. Either way, the handshake
			// has not completed.
			return false;
		}
	}

	/**
	 * Returns the {@link Channel} associated with this connection, or {@code null} if this connection has not yet
	 * started its connection attempt.
	 *
	 * @return the channel associated with this connection, or {@code null} if this connection has not yet started its
	 * connection attempt
	 */
	protected Channel getChannel() {
		return this.connectFuture != null ? this.connectFuture.channel() : null;
	}

	/**
	 * Returns the human-readable name of this connection.
	 *
	 * @return the human-readable name of this connection.
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Returns the name of the server with which this connection should communicate.
	 *
	 * @return the name of the server with which this connection should communicate
	 */
	public abstract String getHost();

	/**
	 * Returns the port on which this connection should connect to the remote server.
	 *
	 * @return the port on which this connection should connect to the remote server
	 */
	public abstract int getPort();

	/**
	 * Returns an optional listener for lifecycle events associated with this connection.
	 *
	 * @return a listener for lifecycle events associated with this connection, or {@code null} if no listener should be
	 * notified of lifecycle events
	 */
	public abstract ApnsConnectionListener getListener();
}
