/* Copyright (c) 2013-2016 RelayRides
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
 * THE SOFTWARE. */

package com.relayrides.pushy.apns;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.relayrides.pushy.apns.proxy.ProxyHandlerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.FailedFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.SucceededFuture;
import io.netty.util.concurrent.ScheduledFuture;

/**
 * <p>An APNs client sends push notifications to the APNs gateway. Clients authenticate themselves to APNs servers in
 * one of two ways: they may either present a TLS certificate to the server at connection time, or they may present
 * authentication tokens for each notification they send. Clients that opt to use TLS-based authentication may send
 * notifications to any topic named in the client certificate. Clients that opt to use token-based authentication may
 * send notifications to any topic for which they have a signing key. Please see Apple's
 * <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/APNSOverview.html">Local
 * and Remote Notification Programming Guide</a> for a detailed discussion of the APNs protocol, topics, and
 * certificate/key provisioning.</p>
 *
 * <p>Clients are constructed using an {@link ApnsClientBuilder}. To use TLS-based client authentication, callers may
 * provide a certificate provisioned by Apple and its accompanying private key at construction time. The certificate and
 * key will be used to authenticate the client and identify the topics to which it can send notifications. Callers may
 * optionally specify an {@link EventLoopGroup} when constructing a new client. If no event loop group is specified,
 * clients will create and manage their own single-thread event loop group. If many clients are operating in parallel,
 * specifying a shared event loop group serves as a mechanism to keep the total number of threads in check. Callers may
 * also want to provide a specific event loop group to take advantage of platform-specific features (i.e.
 * {@code epoll}).</p>
 *
 * <p>If callers do not provide a certificate/private key at construction time, the client will use token-based
 * authentication. Callers must register signing keys for the topics to which the client will send notifications using
 * one of the {@code registerSigningKey} methods.</p>
 *
 * <p>Once a client has been constructed, it must connect to an APNs server before it can begin sending push
 * notifications. Apple provides a production and development gateway; see {@link ApnsClient#PRODUCTION_APNS_HOST} and
 * {@link ApnsClient#DEVELOPMENT_APNS_HOST}. See the
 * <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/CommunicatingwithAPNs.html#//apple_ref/doc/uid/TP40008194-CH11-SW1">Communicating
 * with APNs</a> documentation for additional details.</p>
 *
 * <p>Once a connection has been established, an APNs client will attempt to restore that connection automatically if
 * the connection closes unexpectedly. APNs clients employ an exponential back-off strategy to manage the rate of
 * reconnection attempts. Clients will stop trying to reconnect automatically if disconnected via the
 * {@link ApnsClient#disconnect()} method.</p>
 *
 * <p>Notifications sent by a client to an APNs server are sent asynchronously. A
 * {@link io.netty.util.concurrent.Future io.netty.util.concurrent.Future} is returned immediately when a notification
 * is sent, but will not complete until the attempt to send the notification has failed, the notification has been
 * accepted by the APNs server, or the notification has been rejected by the APNs server. Please note that the
 * {@code Future} returned is a {@code io.netty.util.concurrent.Future}, which is an extension of the
 * {@link java.util.concurrent.Future java.util.concurrent.Future} interface that allows callers to attach listeners
 * that will be notified when the {@code Future} completes.</p>
 *
 * <p>APNs clients are intended to be long-lived, persistent resources. Callers should shut them down when they are no
 * longer needed (i.e. when shutting down the entire application). If an event loop group was specified at construction
 * time, callers should shut down that event loop group when all clients using that group have been disconnected.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.5
 */
public class ApnsClient {
    private final Bootstrap bootstrap;
    private volatile ProxyHandlerFactory proxyHandlerFactory;
    private final boolean shouldShutDownEventLoopGroup;

    private long writeTimeoutMillis = DEFAULT_WRITE_TIMEOUT_MILLIS;
    private Long gracefulShutdownTimeoutMillis;

    private volatile ChannelPromise connectionReadyPromise;
    private volatile ChannelPromise reconnectionPromise;
    private ScheduledFuture scheduledReconnectFuture;
    private long reconnectDelaySeconds = INITIAL_RECONNECT_DELAY_SECONDS;

    private final Map<ApnsPushNotification, Promise<PushNotificationResponse<ApnsPushNotification>>> responsePromises = new IdentityHashMap<>();

    private ApnsClientMetricsListener metricsListener = new NoopMetricsListener();
    private final AtomicLong nextNotificationId = new AtomicLong(0);

    private final boolean useTokenAuthentication;
    private final Map<String, Set<String>> topicsByTeamId = new ConcurrentHashMap <>();
    private final Map<String, AuthenticationTokenSupplier> authenticationTokenSuppliersByTopic = new ConcurrentHashMap <>();

    /**
     * The default write timeout, in milliseconds.
     *
     * @since 0.6
     */
    public static final long DEFAULT_WRITE_TIMEOUT_MILLIS = 20_000;

    /**
     * The hostname for the production APNs gateway.
     *
     * @since 0.5
     */
    public static final String PRODUCTION_APNS_HOST = "api.push.apple.com";

    /**
     * The hostname for the development APNs gateway.
     *
     * @since 0.5
     */
    public static final String DEVELOPMENT_APNS_HOST = "api.development.push.apple.com";

    /**
     * The default (HTTPS) port for communication with the APNs gateway.
     *
     * @since 0.5
     */
    public static final int DEFAULT_APNS_PORT = 443;

    /**
     * <p>An alternative port for communication with the APNs gateway. According to Apple's documentation:</p>
     *
     * <blockquote>You can alternatively use port 2197 when communicating with APNs. You might do this, for example, to
     * allow APNs traffic through your firewall but to block other HTTPS traffic.</blockquote>
     *
     * @see <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/CommunicatingwithAPNs.html#//apple_ref/doc/uid/TP40008194-CH11-SW1">Communicating
     * with APNs</a>
     *
     * @since 0.5
     */
    public static final int ALTERNATE_APNS_PORT = 2197;

    private static final ClientNotConnectedException NOT_CONNECTED_EXCEPTION = new ClientNotConnectedException();

    private static final long INITIAL_RECONNECT_DELAY_SECONDS = 1; // second
    private static final long MAX_RECONNECT_DELAY_SECONDS = 60; // seconds
    static final int PING_IDLE_TIME_MILLIS = 60_000; // milliseconds

    static final String EXPIRED_AUTH_TOKEN_REASON = "ExpiredProviderToken";

    private static final Logger log = LoggerFactory.getLogger(ApnsClient.class);

    protected ApnsClient(final SslContext sslContext, final boolean useTokenAuthentication, final EventLoopGroup eventLoopGroup) {
        this.useTokenAuthentication = useTokenAuthentication;

        this.bootstrap = new Bootstrap();

        if (eventLoopGroup != null) {
            this.bootstrap.group(eventLoopGroup);
            this.shouldShutDownEventLoopGroup = false;
        } else {
            this.bootstrap.group(new NioEventLoopGroup(1));
            this.shouldShutDownEventLoopGroup = true;
        }

        this.bootstrap.channel(SocketChannelClassUtil.getSocketChannelClass(this.bootstrap.config().group()));
        this.bootstrap.option(ChannelOption.TCP_NODELAY, true);
        this.bootstrap.handler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel) throws Exception {
                final ChannelPipeline pipeline = channel.pipeline();

                final ProxyHandlerFactory proxyHandlerFactory = ApnsClient.this.proxyHandlerFactory;

                if (proxyHandlerFactory != null) {
                    pipeline.addFirst(proxyHandlerFactory.createProxyHandler());
                }

                if (ApnsClient.this.writeTimeoutMillis > 0) {
                    pipeline.addLast(new WriteTimeoutHandler(ApnsClient.this.writeTimeoutMillis, TimeUnit.MILLISECONDS));
                }

                pipeline.addLast(sslContext.newHandler(channel.alloc()));
                pipeline.addLast(new ApplicationProtocolNegotiationHandler("") {
                    @Override
                    protected void configurePipeline(final ChannelHandlerContext context, final String protocol) {
                        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                            final ApnsClientHandler apnsClientHandler = new ApnsClientHandler.ApnsClientHandlerBuilder()
                                    .server(false)
                                    .apnsClient(ApnsClient.this)
                                    .authority(((InetSocketAddress) context.channel().remoteAddress()).getHostName())
                                    .useTokenAuthentication(ApnsClient.this.useTokenAuthentication)
                                    .encoderEnforceMaxConcurrentStreams(true)
                                    .build();

                            synchronized (ApnsClient.this.bootstrap) {
                                if (ApnsClient.this.gracefulShutdownTimeoutMillis != null) {
                                    apnsClientHandler.gracefulShutdownTimeoutMillis(ApnsClient.this.gracefulShutdownTimeoutMillis);
                                }
                            }

                            context.pipeline().addLast(new IdleStateHandler(0, 0, PING_IDLE_TIME_MILLIS, TimeUnit.MILLISECONDS));
                            context.pipeline().addLast(apnsClientHandler);

                            // Add this to the end of the queue so any events enqueued by the client handler happen
                            // before we declare victory.
                            context.channel().eventLoop().submit(new Runnable() {

                                @Override
                                public void run() {
                                    final ChannelPromise connectionReadyPromise = ApnsClient.this.connectionReadyPromise;

                                    if (connectionReadyPromise != null) {
                                        connectionReadyPromise.trySuccess();
                                    }
                                }
                            });
                        } else {
                            log.error("Unexpected protocol: {}", protocol);
                            context.close();
                        }
                    }

                    @Override
                    protected void handshakeFailure(final ChannelHandlerContext context, final Throwable cause) throws Exception {
                        final ChannelPromise connectionReadyPromise = ApnsClient.this.connectionReadyPromise;

                        if (connectionReadyPromise != null) {
                            connectionReadyPromise.tryFailure(cause);
                        }

                        super.handshakeFailure(context, cause);
                    }
                });
            }
        });
    }

    /**
     * Sets the maximum amount of time, in milliseconds, that a client will wait to establish a connection with the
     * APNs server before the connection attempt is considered a failure.
     *
     * @param timeoutMillis the maximum amount of time in milliseconds to wait for a connection attempt to complete
     *
     * @since 0.5
     */
    protected void setConnectionTimeout(final int timeoutMillis) {
        synchronized (this.bootstrap) {
            this.bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMillis);
        }
    }

    /**
     * Sets the buffer usage watermark range for this client. When a the amount of buffered and not-yet-flushed data in
     * the client's network channel exceeds the given "high-water" mark, the channel will begin rejecting new data until
     * enough data has been flushed to cross the given "low-water" mark. Notifications sent when the client's network
     * channel is "flooded" will fail with a {@link ClientBusyException}.
     *
     * @param writeBufferWatermark the buffer usage watermark range for the client's network channel
     *
     * @since 0.8.2
     */
    protected void setChannelWriteBufferWatermark(final WriteBufferWaterMark writeBufferWaterMark) {
        synchronized (this.bootstrap) {
            this.bootstrap.option(ChannelOption.WRITE_BUFFER_WATER_MARK, writeBufferWaterMark);
        }
    }

    /**
     * Sets the metrics listener for this client. Metrics listeners gather information that describes the performance
     * and behavior of a client, and are completely optional.
     *
     * @param metricsListener the metrics listener for this client, or {@code null} if this client should not report
     * metrics to a listener
     *
     * @since 0.6
     */
    protected void setMetricsListener(final ApnsClientMetricsListener metricsListener) {
        this.metricsListener = metricsListener != null ? metricsListener : new NoopMetricsListener();
    }

    /**
     * Sets the proxy handler factory to be used to construct proxy handlers when establishing a new connection to the
     * APNs gateway. Proxy handlers are added to the beginning of the client's pipeline. A client's proxy handler
     * factory may be {@code null}, in which case the client will connect to the gateway directly and will not use a
     * proxy. By default, clients will not use a proxy.
     *
     * @param proxyHandlerFactory the proxy handler factory to be used to construct proxy handlers, or {@code null} if
     * this client should not use a proxy
     *
     * @since 0.6
     */
    protected void setProxyHandlerFactory(final ProxyHandlerFactory proxyHandlerFactory) {
        this.proxyHandlerFactory = proxyHandlerFactory;
        this.bootstrap.resolver(proxyHandlerFactory == null ? DefaultAddressResolverGroup.INSTANCE : NoopAddressResolverGroup.INSTANCE);
    }

    /**
     * <p>Sets the write timeout for this client. If an attempt to send a notification to the APNs server takes longer
     * than the given timeout, the connection will be closed (and automatically reconnected later). Note that write
     * timeouts refer to the amount of time taken to <em>send</em> a notification to the server, and not the time taken
     * by the server to process and respond to a notification.</p>
     *
     * <p>Write timeouts should generally be set before starting a connection attempt. Changes to a client's write
     * timeout will take effect after the next connection attempt; changes made to an already-connected client will have
     * no immediate effect.</p>
     *
     * <p>By default, clients have a write timeout of
     * {@value com.relayrides.pushy.apns.ApnsClient#DEFAULT_WRITE_TIMEOUT_MILLIS} milliseconds.</p>
     *
     * @param writeTimeoutMillis the write timeout for this client in milliseconds; if zero, write attempts will never
     * time out
     *
     * @since 0.6
     */
    protected void setWriteTimeout(final long writeTimeoutMillis) {
        this.writeTimeoutMillis = writeTimeoutMillis;
    }

    /**
     * Sets the amount of time (in milliseconds) clients should wait for in-progress requests to complete before closing
     * a connection during a graceful shutdown.
     *
     * @param timeoutMillis the number of milliseconds to wait for in-progress requests to complete before closing a
     * connection
     *
     * @see ApnsClient#disconnect()
     *
     * @since 0.5
     */
    protected void setGracefulShutdownTimeout(final long timeoutMillis) {
        synchronized (this.bootstrap) {
            this.gracefulShutdownTimeoutMillis = timeoutMillis;

            if (this.connectionReadyPromise != null) {
                final ApnsClientHandler handler = this.connectionReadyPromise.channel().pipeline().get(ApnsClientHandler.class);

                if (handler != null) {
                    handler.gracefulShutdownTimeoutMillis(timeoutMillis);
                }
            }
        }
    }

    /**
     * <p>Connects to the given APNs gateway on the default (HTTPS) port
     * ({@value com.relayrides.pushy.apns.ApnsClient#DEFAULT_APNS_PORT}).</p>
     *
     * <p>Once an initial connection has been established and until the client has been explicitly disconnected via the
     * {@link ApnsClient#disconnect()} method, the client will attempt to reconnect automatically if the connection
     * closes unexpectedly. If the connection closes unexpectedly, callers may monitor the status of the reconnection
     * attempt with the {@code Future} returned by the {@link ApnsClient#getReconnectionFuture()} method.</p>
     *
     * @param host the APNs gateway to which to connect
     *
     * @return a {@code Future} that will succeed when the client has connected to the gateway and is ready to send
     * push notifications
     *
     * @see ApnsClient#PRODUCTION_APNS_HOST
     * @see ApnsClient#DEVELOPMENT_APNS_HOST
     *
     * @since 0.5
     */
    public Future<Void> connect(final String host) {
        return this.connect(host, DEFAULT_APNS_PORT);
    }

    /**
     * <p>Connects to the given APNs gateway on the given port.</p>
     *
     * <p>Once an initial connection has been established and until the client has been explicitly disconnected via the
     * {@link ApnsClient#disconnect()} method, the client will attempt to reconnect automatically if the connection
     * closes unexpectedly. If the connection closes unexpectedly, callers may monitor the status of the reconnection
     * attempt with the {@code Future} returned by the {@link ApnsClient#getReconnectionFuture()} method.</p>
     *
     * @param host the APNs gateway to which to connect
     * @param port the port on which to connect to the APNs gateway
     *
     * @return a {@code Future} that will succeed when the client has connected to the gateway and is ready to send
     * push notifications
     *
     * @see ApnsClient#PRODUCTION_APNS_HOST
     * @see ApnsClient#DEVELOPMENT_APNS_HOST
     * @see ApnsClient#DEFAULT_APNS_PORT
     * @see ApnsClient#ALTERNATE_APNS_PORT
     *
     * @since 0.5
     */
    public Future<Void> connect(final String host, final int port) {
        final Future<Void> connectionReadyFuture;

        if (this.bootstrap.config().group().isShuttingDown() || this.bootstrap.config().group().isShutdown()) {
            connectionReadyFuture = new FailedFuture<>(GlobalEventExecutor.INSTANCE,
                    new IllegalStateException("Client's event loop group has been shut down and cannot be restarted."));
        } else {
            synchronized (this.bootstrap) {
                // We only want to begin a connection attempt if one is not already in progress or complete; if we already
                // have a connection future, just return the existing promise.
                if (this.connectionReadyPromise == null) {
                    this.metricsListener.handleConnectionAttemptStarted(this);

                    final ChannelFuture connectFuture = this.bootstrap.connect(host, port);
                    this.connectionReadyPromise = connectFuture.channel().newPromise();

                    connectFuture.addListener(new GenericFutureListener<ChannelFuture> () {

                        @Override
                        public void operationComplete(final ChannelFuture future) throws Exception {
                            if (!future.isSuccess()) {
                                final ChannelPromise connectionReadyPromise = ApnsClient.this.connectionReadyPromise;

                                if (connectionReadyPromise != null) {
                                    // This may seem spurious, but our goal here is to accurately report the cause of
                                    // connection failure; if we just wait for connection closure, we won't be able to
                                    // tell callers anything more specific about what went wrong.
                                    connectionReadyPromise.tryFailure(future.cause());
                                }
                            }
                        }
                    });

                    connectFuture.channel().closeFuture().addListener(new GenericFutureListener<ChannelFuture> () {

                        @Override
                        public void operationComplete(final ChannelFuture future) throws Exception {
                            synchronized (ApnsClient.this.bootstrap) {
                                if (ApnsClient.this.connectionReadyPromise != null) {
                                    // We always want to try to fail the "connection ready" promise if the connection
                                    // closes; if it has already succeeded, this will have no effect.
                                    ApnsClient.this.connectionReadyPromise.tryFailure(
                                            new IllegalStateException("Channel closed before HTTP/2 preface completed."));

                                    ApnsClient.this.connectionReadyPromise = null;
                                }

                                if (ApnsClient.this.reconnectionPromise != null) {
                                    log.debug("Disconnected. Next automatic reconnection attempt in {} seconds.", ApnsClient.this.reconnectDelaySeconds);

                                    ApnsClient.this.scheduledReconnectFuture = future.channel().eventLoop().schedule(new Runnable() {

                                        @Override
                                        public void run() {
                                            log.debug("Attempting to reconnect.");
                                            ApnsClient.this.connect(host, port);
                                        }
                                    }, ApnsClient.this.reconnectDelaySeconds, TimeUnit.SECONDS);

                                    ApnsClient.this.reconnectDelaySeconds = Math.min(ApnsClient.this.reconnectDelaySeconds, MAX_RECONNECT_DELAY_SECONDS);
                                }
                            }

                            // After everything else is done, clear the remaining "waiting for a response" promises. We
                            // want to do this after everything else has wrapped up (i.e. we submit it to the end of the
                            // event queue) so any promises that have "mark as success" jobs already in the queue have
                            // a chance to fire first.
                            future.channel().eventLoop().submit(new Runnable() {

                                @Override
                                public void run() {
                                    for (final Promise<PushNotificationResponse<ApnsPushNotification>> responsePromise : ApnsClient.this.responsePromises.values()) {
                                        responsePromise.tryFailure(new ClientNotConnectedException("Client disconnected unexpectedly."));
                                    }

                                    ApnsClient.this.responsePromises.clear();
                                }
                            });
                        }
                    });

                    this.connectionReadyPromise.addListener(new GenericFutureListener<ChannelFuture>() {

                        @Override
                        public void operationComplete(final ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                synchronized (ApnsClient.this.bootstrap) {
                                    if (ApnsClient.this.reconnectionPromise != null) {
                                        log.info("Connection to {} restored.", future.channel().remoteAddress());
                                        ApnsClient.this.reconnectionPromise.trySuccess();
                                    } else {
                                        log.info("Connected to {}.", future.channel().remoteAddress());
                                    }

                                    ApnsClient.this.reconnectDelaySeconds = INITIAL_RECONNECT_DELAY_SECONDS;
                                    ApnsClient.this.reconnectionPromise = future.channel().newPromise();
                                }

                                ApnsClient.this.metricsListener.handleConnectionAttemptSucceeded(ApnsClient.this);
                            } else {
                                log.info("Failed to connect.", future.cause());

                                ApnsClient.this.metricsListener.handleConnectionAttemptFailed(ApnsClient.this);
                            }
                        }
                    });
                }

                connectionReadyFuture = this.connectionReadyPromise;
            }
        }

        return connectionReadyFuture;
    }

    /**
     * Indicates whether this client is connected to the APNs gateway and ready to send push notifications.
     *
     * @return {@code true} if this client is connected and ready to send notifications or {@code false} otherwise
     *
     * @since 0.5
     */
    public boolean isConnected() {
        final ChannelPromise connectionReadyPromise = this.connectionReadyPromise;
        return (connectionReadyPromise != null && connectionReadyPromise.isSuccess());
    }

    /**
     * <p>Returns a {@code Future} that will succeed when the client has re-established a connection to the APNs gateway.
     * Callers may use this method to determine when it is safe to resume sending notifications after a send attempt
     * fails with a {@link ClientNotConnectedException}.</p>
     *
     * <p>If the client is already connected, the {@code Future} returned by this method will succeed immediately. If
     * the client was not previously connected (either because it has never been connected or because it was explicitly
     * disconnected via the {@link ApnsClient#disconnect()} method), the {@code Future} returned by this method will
     * fail immediately with an {@link IllegalStateException}.</p>
     *
     * @return a {@code Future} that will succeed when the client has established a connection to the APNs gateway
     *
     * @since 0.5
     */
    public Future<Void> getReconnectionFuture() {
        final Future<Void> reconnectionFuture;

        synchronized (this.bootstrap) {
            if (this.isConnected()) {
                reconnectionFuture = this.connectionReadyPromise.channel().newSucceededFuture();
            } else if (this.reconnectionPromise != null) {
                // If we're not connected, but have a reconnection promise, we're in the middle of a reconnection
                // attempt.
                reconnectionFuture = this.reconnectionPromise;
            } else {
                // We're not connected and have no reconnection future, which means we've either never connected or have
                // explicitly disconnected.
                reconnectionFuture = new FailedFuture<>(GlobalEventExecutor.INSTANCE,
                        new IllegalStateException("Client was not previously connected."));
            }
        }

        return reconnectionFuture;
    }

    /**
     * <p>Registers a private signing key for the given topics. Clears any topics and keys previously associated with
     * the given team.</p>
     *
     * <p>Callers using token-based authentication <em>must</em> register signing keys for all topics to which they
     * intend to send notifications. Callers <em>must not</em> attempt to register signing keys when using TLS-based
     * client authentication. Tokens may be registered at any time in a client's life-cycle.</p>
     *
     * @param signingKeyPemFile a PEM file that contains a PKCS#8-formatted elliptic-curve private key with which to
     * sign authentication tokens
     * @param teamId the Apple-issued, ten-character identifier for the team to which the given private key belongs
     * @param keyId the Apple-issued, ten-character identifier for the given private key
     * @param topics the topics to which the given signing key is applicable
     *
     * @throws IllegalStateException if this client uses TLS-based authentication instead of token-based authentication
     * @throws InvalidKeyException if the given key is invalid for any reason
     * @throws NoSuchAlgorithmException if the JRE does not support the required token-signing algorithm
     * @throws IOException if a private key could not be loaded from the given file for any reason
     *
     * @since 0.9
     */
    public void registerSigningKey(final File signingKeyPemFile, final String teamId, final String keyId, final Collection<String> topics) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        this.registerSigningKey(signingKeyPemFile, teamId, keyId, topics.toArray(new String[0]));
    }

    /**
     * <p>Registers a private signing key for the given topics. Clears any topics and keys previously associated with
     * the given team.</p>
     *
     * <p>Callers using token-based authentication <em>must</em> register signing keys for all topics to which they
     * intend to send notifications. Callers <em>must not</em> attempt to register signing keys when using TLS-based
     * client authentication. Tokens may be registered at any time in a client's life-cycle.</p>
     *
     * @param signingKeyPemFile a PEM file that contains a PKCS#8-formatted elliptic-curve private key with which to
     * sign authentication tokens
     * @param teamId the Apple-issued, ten-character identifier for the team to which the given private key belongs
     * @param keyId the Apple-issued, ten-character identifier for the given private key
     * @param topics the topics to which the given signing key is applicable
     *
     * @throws IllegalStateException if this client uses TLS-based authentication instead of token-based authentication
     * @throws InvalidKeyException if the given key is invalid for any reason
     * @throws NoSuchAlgorithmException if the JRE does not support the required token-signing algorithm
     * @throws IOException if a private key could not be loaded from the given file for any reason
     *
     * @since 0.9
     */
    public void registerSigningKey(final File signingKeyPemFile, final String teamId, final String keyId, final String... topics) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        try (final FileInputStream signingKeyInputStream = new FileInputStream(signingKeyPemFile)) {
            this.registerSigningKey(signingKeyInputStream, teamId, keyId, topics);
        }
    }

    /**
     * <p>Registers a private signing key for the given topics. Clears any topics and keys previously associated with
     * the given team.</p>
     *
     * <p>Callers using token-based authentication <em>must</em> register signing keys for all topics to which they
     * intend to send notifications. Callers <em>must not</em> attempt to register signing keys when using TLS-based
     * client authentication. Tokens may be registered at any time in a client's life-cycle.</p>
     *
     * @param signingKeyInputStream an input stream that provides a PEM-encoded, PKCS#8-formatted elliptic-curve private
     * key with which to sign authentication tokens
     * @param teamId the Apple-issued, ten-character identifier for the team to which the given private key belongs
     * @param keyId the Apple-issued, ten-character identifier for the given private key
     * @param topics the topics to which the given signing key is applicable
     *
     * @throws IllegalStateException if this client uses TLS-based authentication instead of token-based authentication
     * @throws InvalidKeyException if the given key is invalid for any reason
     * @throws NoSuchAlgorithmException if the JRE does not support the required token-signing algorithm
     * @throws IOException if a private key could not be loaded from the given input stream for any reason
     *
     * @since 0.9
     */
    public void registerSigningKey(final InputStream signingKeyInputStream, final String teamId, final String keyId, final Collection<String> topics) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        this.registerSigningKey(signingKeyInputStream, teamId, keyId, topics.toArray(new String[0]));
    }

    /**
     * <p>Registers a private signing key for the given topics. Clears any topics and keys previously associated with
     * the given team.</p>
     *
     * <p>Callers using token-based authentication <em>must</em> register signing keys for all topics to which they
     * intend to send notifications. Callers <em>must not</em> attempt to register signing keys when using TLS-based
     * client authentication. Tokens may be registered at any time in a client's life-cycle.</p>
     *
     * @param signingKeyInputStream an input stream that provides a PEM-encoded, PKCS#8-formatted elliptic-curve private
     * key with which to sign authentication tokens
     * @param teamId the Apple-issued, ten-character identifier for the team to which the given private key belongs
     * @param keyId the Apple-issued, ten-character identifier for the given private key
     * @param topics the topics to which the given signing key is applicable
     *
     * @throws IllegalStateException if this client uses TLS-based authentication instead of token-based authentication
     * @throws InvalidKeyException if the given key is invalid for any reason
     * @throws NoSuchAlgorithmException if the JRE does not support the required token-signing algorithm
     * @throws IOException if a private key could not be loaded from the given input stream for any reason
     *
     * @since 0.9
     */
    public void registerSigningKey(final InputStream signingKeyInputStream, final String teamId, final String keyId, final String... topics) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        final ECPrivateKey signingKey;
        {
            final String base64EncodedPrivateKey;
            {
                final StringBuilder privateKeyBuilder = new StringBuilder();

                final BufferedReader reader = new BufferedReader(new InputStreamReader(signingKeyInputStream));
                boolean haveReadHeader = false;
                boolean haveReadFooter = false;

                for (String line; (line = reader.readLine()) != null; ) {
                    if (!haveReadHeader) {
                        if (line.contains("BEGIN PRIVATE KEY")) {
                            haveReadHeader = true;
                            continue;
                        }
                    } else {
                        if (line.contains("END PRIVATE KEY")) {
                            haveReadFooter = true;
                            break;
                        } else {
                            privateKeyBuilder.append(line);
                        }
                    }
                }

                if (!(haveReadHeader && haveReadFooter)) {
                    throw new IOException("Could not find private key header/footer");
                }

                base64EncodedPrivateKey = privateKeyBuilder.toString();
            }

            final ByteBuf wrappedEncodedPrivateKey = Unpooled.wrappedBuffer(base64EncodedPrivateKey.getBytes(StandardCharsets.US_ASCII));

            try {
                final ByteBuf decodedPrivateKey = Base64.decode(wrappedEncodedPrivateKey);

                try {
                    final byte[] keyBytes = new byte[decodedPrivateKey.readableBytes()];
                    decodedPrivateKey.readBytes(keyBytes);

                    final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
                    final KeyFactory keyFactory = KeyFactory.getInstance("EC");
                    signingKey = (ECPrivateKey) keyFactory.generatePrivate(keySpec);
                } catch (final InvalidKeySpecException e) {
                    throw new InvalidKeyException(e);
                } finally {
                    decodedPrivateKey.release();
                }
            } finally {
                wrappedEncodedPrivateKey.release();
            }
        }

        this.registerSigningKey(signingKey, teamId, keyId, topics);
    }

    /**
     * <p>Registers a private signing key for the given topics. Clears any topics and keys previously associated with
     * the given team.</p>
     *
     * <p>Callers using token-based authentication <em>must</em> register signing keys for all topics to which they
     * intend to send notifications. Callers <em>must not</em> attempt to register signing keys when using TLS-based
     * client authentication. Tokens may be registered at any time in a client's life-cycle.</p>
     *
     * @param signingKey the private key with which to sign authentication tokens
     * @param teamId the Apple-issued, ten-character identifier for the team to which the given private key belongs
     * @param keyId the Apple-issued, ten-character identifier for the given private key
     * @param topics the topics to which the given signing key is applicable
     *
     * @throws IllegalStateException if this client uses TLS-based authentication instead of token-based authentication
     * @throws InvalidKeyException if the given key is invalid for any reason
     * @throws NoSuchAlgorithmException if the JRE does not support the required token-signing algorithm
     *
     * @since 0.9
     */
    public void registerSigningKey(final ECPrivateKey signingKey, final String teamId, final String keyId, final Collection<String> topics) throws InvalidKeyException, NoSuchAlgorithmException {
        this.registerSigningKey(signingKey, teamId, keyId, topics.toArray(new String[0]));
    }

    /**
     * <p>Registers a private signing key for the given topics. Clears any topics and keys previously associated with
     * the given team.</p>
     *
     * <p>Callers using token-based authentication <em>must</em> register signing keys for all topics to which they
     * intend to send notifications. Callers <em>must not</em> attempt to register signing keys when using TLS-based
     * client authentication. Tokens may be registered at any time in a client's life-cycle.</p>
     *
     * @param signingKey the private key with which to sign authentication tokens
     * @param teamId the Apple-issued, ten-character identifier for the team to which the given private key belongs
     * @param keyId the Apple-issued, ten-character identifier for the given private key
     * @param topics the topics to which the given signing key is applicable
     *
     * @throws IllegalStateException if this client uses TLS-based authentication instead of token-based authentication
     * @throws InvalidKeyException if the given key is invalid for any reason
     * @throws NoSuchAlgorithmException if the JRE does not support the required token-signing algorithm
     *
     * @since 0.9
     */
    public void registerSigningKey(final ECPrivateKey signingKey, final String teamId, final String keyId, final String... topics) throws InvalidKeyException, NoSuchAlgorithmException {
        if (!this.useTokenAuthentication) {
            throw new IllegalStateException("Cannot register signing keys with clients that use TLS-based authentication.");
        }

        this.removeKeyForTeam(teamId);

        final AuthenticationTokenSupplier tokenSupplier = new AuthenticationTokenSupplier(teamId, keyId, signingKey);

        final Set<String> topicSet = new HashSet<>();

        for (final String topic : topics) {
            topicSet.add(topic);
            this.authenticationTokenSuppliersByTopic.put(topic, tokenSupplier);
        }

        this.topicsByTeamId.put(teamId, topicSet);
    }

    /**
     * Removes all registered keys and associated topics for the given team.
     *
     * @param teamId the Apple-issued, ten-character identifier for the team for which to remove keys and topics
     */
    public void removeKeyForTeam(final String teamId) {
        final Set<String> oldTopics = this.topicsByTeamId.remove(teamId);

        if (oldTopics != null) {
            for (final String topic : oldTopics) {
                this.authenticationTokenSuppliersByTopic.remove(topic);
            }
        }
    }

    protected AuthenticationTokenSupplier getAuthenticationTokenSupplierForTopic(final String topic) throws NoKeyForTopicException {
        final AuthenticationTokenSupplier supplier = this.authenticationTokenSuppliersByTopic.get(topic);

        if (supplier == null) {
            throw new NoKeyForTopicException("No signing key found for topic " + topic);
        }

        return supplier;
    }

    /**
     * <p>Sends a push notification to the APNs gateway.</p>
     *
     * <p>This method returns a {@code Future} that indicates whether the notification was accepted or rejected by the
     * gateway. If the notification was accepted, it may be delivered to its destination device at some time in the
     * future, but final delivery is not guaranteed. Rejections should be considered permanent failures, and callers
     * should <em>not</em> attempt to re-send the notification.</p>
     *
     * <p>The returned {@code Future} may fail with an exception if the notification could not be sent. Failures to
     * <em>send</em> a notification to the gateway—i.e. those that fail with exceptions—should generally be considered
     * non-permanent, and callers should attempt to re-send the notification when the underlying problem has been
     * resolved.</p>
     *
     * <p>In particular, attempts to send a notification when the client is not connected will fail with a
     * {@link ClientNotConnectedException}. If the client was previously connected and has not been explicitly
     * disconnected (via the {@link ApnsClient#disconnect()} method), the client will attempt to reconnect
     * automatically. Callers may wait for a reconnection attempt to complete by waiting for the {@code Future} returned
     * by the {@link ApnsClient#getReconnectionFuture()} method.</p>
     *
     * @param notification the notification to send to the APNs gateway
     *
     * @param <T> the type of notification to be sent
     *
     * @return a {@code Future} that will complete when the notification has been either accepted or rejected by the
     * APNs gateway
     *
     * @since 0.8
     */
    public <T extends ApnsPushNotification> Future<PushNotificationResponse<T>> sendNotification(final T notification) {
        return this.sendNotification(notification, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T extends ApnsPushNotification> Future<PushNotificationResponse<T>> sendNotification(final T notification, final Promise<PushNotificationResponse<ApnsPushNotification>> promise) {
        final Future<PushNotificationResponse<T>> responseFuture;
        final long notificationId = this.nextNotificationId.getAndIncrement();

        // Instead of synchronizing here, we keep a final reference to the connection ready promise. We can get away
        // with this because we're not changing the state of the connection or its promises. Keeping a reference ensures
        // we won't suddenly "lose" the channel and get a NullPointerException, but risks sending a notification after
        // things have shut down. In that case, though, the returned futures should fail quickly, and the benefit of
        // not synchronizing for every write seems worth it.
        final ChannelPromise connectionReadyPromise = this.connectionReadyPromise;

        if (connectionReadyPromise != null && connectionReadyPromise.isSuccess() && connectionReadyPromise.channel().isActive()) {
            final Promise<PushNotificationResponse<ApnsPushNotification>> responsePromise;

            if (promise != null) {
                responsePromise = promise;
            } else {
                responsePromise = new DefaultPromise<>(connectionReadyPromise.channel().eventLoop());
            }

            connectionReadyPromise.channel().eventLoop().submit(new Runnable() {

                @Override
                public void run() {
                    if (ApnsClient.this.responsePromises.containsKey(notification)) {
                        responsePromise.setFailure(new IllegalStateException(
                                "The given notification has already been sent and not yet resolved."));
                    } else {
                        // We want to do this inside the channel's event loop so we can be sure that only one thread is
                        // modifying responsePromises.
                        ApnsClient.this.responsePromises.put(notification, responsePromise);
                    }
                }
            });

            connectionReadyPromise.channel().writeAndFlush(notification).addListener(new GenericFutureListener<ChannelFuture>() {

                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        ApnsClient.this.metricsListener.handleNotificationSent(ApnsClient.this, notificationId);
                    } else {
                        log.debug("Failed to write push notification: {}", notification, future.cause());

                        // This will always be called from inside the channel's event loop, so we don't have to worry
                        // about synchronization.
                        ApnsClient.this.responsePromises.remove(notification);
                        responsePromise.tryFailure(future.cause());
                    }
                }
            });

            responseFuture = (Future) responsePromise;
        } else {
            log.debug("Failed to send push notification because client is not connected: {}", notification);
            responseFuture = new FailedFuture<>(
                    GlobalEventExecutor.INSTANCE, NOT_CONNECTED_EXCEPTION);
        }

        responseFuture.addListener(new GenericFutureListener<Future<PushNotificationResponse<T>>>() {

            @Override
            public void operationComplete(final Future<PushNotificationResponse<T>> future) throws Exception {
                if (future.isSuccess()) {
                    final PushNotificationResponse<T> response = future.getNow();

                    if (response.isAccepted()) {
                        ApnsClient.this.metricsListener.handleNotificationAccepted(ApnsClient.this, notificationId);
                    } else {
                        ApnsClient.this.metricsListener.handleNotificationRejected(ApnsClient.this, notificationId);
                    }
                } else {
                    ApnsClient.this.metricsListener.handleWriteFailure(ApnsClient.this, notificationId);
                }
            }
        });

        return responseFuture;
    }

    protected void handlePushNotificationResponse(final PushNotificationResponse<ApnsPushNotification> response) {
        log.debug("Received response from APNs gateway: {}", response);

        // This will always be called from inside the channel's event loop, so we don't have to worry about
        // synchronization.
        final Promise<PushNotificationResponse<ApnsPushNotification>> responsePromise =
                this.responsePromises.remove(response.getPushNotification());

        if (EXPIRED_AUTH_TOKEN_REASON.equals(response.getRejectionReason())) {
            this.sendNotification(response.getPushNotification(), responsePromise);
        } else {
            responsePromise.setSuccess(response);
        }
    }

    protected void handleServerError(final ApnsPushNotification pushNotification, final String message) {
        log.warn("APNs server reported an internal error when sending {}.", pushNotification);

        // This will always be called from inside the channel's event loop, so we don't have to worry about
        // synchronization.
        this.responsePromises.remove(pushNotification).tryFailure(new ApnsServerException(message));
    }

    /**
     * <p>Gracefully disconnects from the APNs gateway. The disconnection process will wait until notifications that
     * have been sent to the APNs server have been either accepted or rejected. Note that some notifications passed to
     * {@link com.relayrides.pushy.apns.ApnsClient#sendNotification(ApnsPushNotification)} may still be enqueued and
     * not yet sent by the time the shutdown process begins; the {@code Futures} associated with those notifications
     * will fail.</p>
     *
     * <p>The returned {@code Future} will be marked as complete when the connection has closed completely. If the
     * connection is already closed when this method is called, the returned {@code Future} will be marked as complete
     * immediately.</p>
     *
     * <p>If a non-null {@code EventLoopGroup} was provided at construction time, clients may be reconnected and reused
     * after they have been disconnected. If no event loop group was provided at construction time, clients may not be
     * restarted after they have been disconnected via this method.</p>
     *
     * @return a {@code Future} that will be marked as complete when the connection has been closed
     *
     * @since 0.5
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Future<Void> disconnect() {
        log.info("Disconnecting.");
        final Future<Void> disconnectFuture;

        synchronized (this.bootstrap) {
            this.reconnectionPromise = null;
            if (this.scheduledReconnectFuture != null) {
                this.scheduledReconnectFuture.cancel(true);
            }

            final Future<Void> channelCloseFuture;

            if (this.connectionReadyPromise != null) {
                channelCloseFuture = this.connectionReadyPromise.channel().close();
            } else {
                channelCloseFuture = new SucceededFuture<>(GlobalEventExecutor.INSTANCE, null);
            }

            if (this.shouldShutDownEventLoopGroup) {
                // Wait for the channel to close before we try to shut down the event loop group
                channelCloseFuture.addListener(new GenericFutureListener<Future<Void>>() {

                    @Override
                    public void operationComplete(final Future<Void> future) throws Exception {
                        ApnsClient.this.bootstrap.config().group().shutdownGracefully();
                    }
                });

                // Since the termination future for the event loop group is a Future<?> instead of a Future<Void>,
                // we'll need to create our own promise and then notify it when the termination future completes.
                disconnectFuture = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);

                this.bootstrap.config().group().terminationFuture().addListener(new GenericFutureListener() {

                    @Override
                    public void operationComplete(final Future future) throws Exception {
                        assert disconnectFuture instanceof DefaultPromise;
                        ((DefaultPromise<Void>) disconnectFuture).trySuccess(null);
                    }
                });
            } else {
                // We're done once we've closed the channel, so we can return the closure future directly.
                disconnectFuture = channelCloseFuture;
            }
        }

        return disconnectFuture;
    }
}
