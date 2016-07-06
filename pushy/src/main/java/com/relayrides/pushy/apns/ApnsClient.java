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

import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.relayrides.pushy.apns.proxy.ProxyHandlerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
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

/**
 * <p>An APNs client sends push notifications to the APNs gateway. An APNs client connects to the APNs server and, as
 * part of a TLS handshake, presents a certificate that identifies the client and the "topics" to which it can send
 * push notifications. Please see Apple's
 * <a href="https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/">Local
 * and Remote Notification Programming Guide</a> for detailed discussion of
 * <a href="https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ApplePushService.html#//apple_ref/doc/uid/TP40008194-CH100-SW9">topics</a>
 * and <a href="https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ProvisioningDevelopment.html#//apple_ref/doc/uid/TP40008194-CH104-SW1">certificate
 * provisioning</a>.</p>
 *
 * <p>Clients are constructed using an {@link ApnsClientBuilder}. To construct a client, callers will need to provide
 * the certificate provisioned by Apple and its accompanying private key. The certificate and key will be used to
 * authenticate the client and identify the topics to which it can send notifications. Callers may optionally specify an
 * {@link EventLoopGroup} when constructing a new client. If no event loop group is specified, clients will create and
 * manage their own single-thread event loop group. If many clients are operating in parallel, specifying a shared event
 * loop group serves as a mechanism to keep the total number of threads in check. Callers may also want to provide a
 * specific event loop group to take advantage of platform-specific features (i.e. epoll).</p>
 *
 * <p>Once a client has been constructed, it must connect to an APNs server before it can begin sending push
 * notifications. Apple provides a production and development gateway; see {@link ApnsClient#PRODUCTION_APNS_HOST} and
 * {@link ApnsClient#DEVELOPMENT_APNS_HOST}. See the
 * <a href="https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/APNsProviderAPI.html#//apple_ref/doc/uid/TP40008194-CH101-SW1">APNs
 * Provider API</a> documentation for additional details.</p>
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
 * @param <T> the type of notification handled by the client
 *
 * @since 0.5
 */
public class ApnsClient<T extends ApnsPushNotification> {
    private final Bootstrap bootstrap;
    private final boolean shouldShutDownEventLoopGroup;

    private final Map<String, AuthenticationTokenSupplier> authenticationTokenSuppliersByTeamId = new HashMap<>();
    private final Map<String, String> teamIdsByTopic = new HashMap<>();

    private long writeTimeoutMillis = DEFAULT_WRITE_TIMEOUT_MILLIS;
    private Long gracefulShutdownTimeoutMillis;

    private int maxUnflushedNotifications = DEFAULT_MAX_UNFLUSHED_NOTIFICATIONS;
    private long flushAfterIdleTimeMillis = DEFAULT_FLUSH_AFTER_IDLE_MILLIS;

    private volatile ChannelPromise connectionReadyPromise;
    private volatile ChannelPromise reconnectionPromise;
    private long reconnectDelaySeconds = INITIAL_RECONNECT_DELAY_SECONDS;

    private final Map<T, Promise<PushNotificationResponse<T>>> responsePromises = new IdentityHashMap<>();

    private volatile ProxyHandlerFactory proxyHandlerFactory;
    private ApnsClientMetricsListener metricsListener = new NoopMetricsListener();

    private final AtomicLong nextNotificationId = new AtomicLong(0);

    /**
     * The default write timeout, in milliseconds.
     *
     * @since 0.6
     */
    public static final long DEFAULT_WRITE_TIMEOUT_MILLIS = 20_000;

    /**
     * The default inactivity period after which all unflushed notifications will be sent to the APNs server.
     *
     * @since 0.7
     */
    public static final long DEFAULT_FLUSH_AFTER_IDLE_MILLIS = 50;

    /**
     * The default maximum number of notifications that may be buffered by a client before being sent to the APNs
     * server.
     *
     * @since 0.7
     */
    public static final int DEFAULT_MAX_UNFLUSHED_NOTIFICATIONS = 128;

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
     * @see <a href="https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/APNsProviderAPI.html#//apple_ref/doc/uid/TP40008194-CH101-SW12">APNs
     * Provider API, Connections</a>
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

    protected ApnsClient(final SslContext sslContext, final EventLoopGroup eventLoopGroup) {
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
                            final ApnsClientHandler<T> apnsClientHandler = new ApnsClientHandler.ApnsClientHandlerBuilder<T>()
                                    .server(false)
                                    .apnsClient(ApnsClient.this)
                                    .authority(((InetSocketAddress) context.channel().remoteAddress()).getHostName())
                                    .maxUnflushedNotifications(ApnsClient.this.maxUnflushedNotifications)
                                    .encoderEnforceMaxConcurrentStreams(true)
                                    .build();

                            synchronized (ApnsClient.this.bootstrap) {
                                if (ApnsClient.this.gracefulShutdownTimeoutMillis != null) {
                                    apnsClientHandler.gracefulShutdownTimeoutMillis(ApnsClient.this.gracefulShutdownTimeoutMillis);
                                }
                            }

                            context.pipeline().addLast(new IdleStateHandler(0, ApnsClient.this.flushAfterIdleTimeMillis, PING_IDLE_TIME_MILLIS, TimeUnit.MILLISECONDS));
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
     * <p>Sets the thresholds at which this client will flush notifications enqueued for sending to the APNs server. By
     * default, notifications will be flushed after
     * {@value com.relayrides.pushy.apns.ApnsClient#DEFAULT_MAX_UNFLUSHED_NOTIFICATIONS} unflushed
     * notifications have been enqueued or {@value com.relayrides.pushy.apns.ApnsClient#DEFAULT_FLUSH_AFTER_IDLE_MILLIS}
     * milliseconds of inactivity have elapsed.</p>
     *
     * <p>Callers may set both thresholds to zero to flush all notifications immediately, which will reduce latency at
     * the expense of decreasing efficiency and throughput. Changes to the flushing thresholds will take effect on the
     * next connection attempt.</p>
     *
     * @param maxUnflushedNotifications The maximum number of notifications that may be enqueued before the sending
     * queue is flushed. Must be positive if {@code maxIdleTimeMillis} is also positive or zero if
     * {@code maxIdleTimeMillis} is also zero. If zero, notifications are always sent immediately.
     * @param maxIdleTimeMillis The maximum amount of time, in milliseconds, since the last attempt to send a
     * notification that may elapse before the sending queue is flushed. Must be positive if
     * {@code maxUnflushedNotifications} is also positive or zero if {@code maxUnflushedNotifications} is also zero. If
     * zero, notifications are always sent immediately.
     *
     * @since 0.7
     */
    protected void setFlushThresholds(final int maxUnflushedNotifications, final long maxIdleTimeMillis) {
        if ((maxUnflushedNotifications > 0 && maxIdleTimeMillis > 0) || (maxUnflushedNotifications == 0 && maxIdleTimeMillis == 0)) {
            synchronized (this.bootstrap) {
                this.maxUnflushedNotifications = maxUnflushedNotifications;
                this.flushAfterIdleTimeMillis = maxIdleTimeMillis;
            }
        } else {
            throw new IllegalArgumentException("Notification count and idle time must both be positive or both be zero.");
        }
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
                @SuppressWarnings("rawtypes")
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

                                    future.channel().eventLoop().schedule(new Runnable() {

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
                                    for (final Promise<PushNotificationResponse<T>> responsePromise : ApnsClient.this.responsePromises.values()) {
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

    /*
     * Waits for the initial SETTINGS frame from the server after connecting. For testing purposes only.
     */
    void waitForInitialSettings() throws InterruptedException {
        this.connectionReadyPromise.channel().pipeline().get(ApnsClientHandler.class).waitForInitialSettings();
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

    public void registerSigningKey(final String teamId, final String keyId, final PrivateKey signingKey) throws InvalidKeyException, NoSuchAlgorithmException {
        this.authenticationTokenSuppliersByTeamId.put(teamId, new AuthenticationTokenSupplier(teamId, keyId, signingKey));
    }

    public void registerTopicsForTeamId(final String teamId, final String... topics) {
        this.registerTopicsForTeamId(teamId, Arrays.asList(topics));
    }

    public void registerTopicsForTeamId(final String teamId, final Collection<String> topics) {
        for (final String topic : topics) {
            this.teamIdsByTopic.put(topic, teamId);
        }
    }

    protected AuthenticationTokenSupplier getAuthenticationTokenSupplierForTopic(final String topic) throws NoKeyForTopicException {
        final String teamId = this.teamIdsByTopic.get(topic);

        if (teamId == null) {
            throw new NoKeyForTopicException("No team found for topic " + topic);
        }

        final AuthenticationTokenSupplier supplier = this.authenticationTokenSuppliersByTeamId.get(teamId);

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
     * <p>Note that, depending on this client's flushing thresholds, notifications may be enqueued so they may be sent
     * in bulk to increase efficiency and throughput at the expense of latency.</p>
     *
     * @param notification the notification to send to the APNs gateway
     *
     * @return a {@code Future} that will complete when the notification has been either accepted or rejected by the
     * APNs gateway
     *
     * @see ApnsClient#setFlushThresholds(int, long)
     *
     * @since 0.5
     */
    public Future<PushNotificationResponse<T>> sendNotification(final T notification) {
        return this.sendNotification(notification, null);
    }

    private Future<PushNotificationResponse<T>> sendNotification(final T notification, final Promise<PushNotificationResponse<T>> promise) {
        final Future<PushNotificationResponse<T>> responseFuture;
        final long notificationId = this.nextNotificationId.getAndIncrement();

        // Instead of synchronizing here, we keep a final reference to the connection ready promise. We can get away
        // with this because we're not changing the state of the connection or its promises. Keeping a reference ensures
        // we won't suddenly "lose" the channel and get a NullPointerException, but risks sending a notification after
        // things have shut down. In that case, though, the returned futures should fail quickly, and the benefit of
        // not synchronizing for every write seems worth it.
        final ChannelPromise connectionReadyPromise = this.connectionReadyPromise;

        if (connectionReadyPromise != null && connectionReadyPromise.isSuccess() && connectionReadyPromise.channel().isActive()) {
            final Promise<PushNotificationResponse<T>> responsePromise = promise != null ?
                    promise : new DefaultPromise<PushNotificationResponse<T>>(connectionReadyPromise.channel().eventLoop());

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

            connectionReadyPromise.channel().write(notification).addListener(new GenericFutureListener<ChannelFuture>() {

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

            responseFuture = responsePromise;
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

    protected void handlePushNotificationResponse(final PushNotificationResponse<T> response) {
        log.debug("Received response from APNs gateway: {}", response);

        // This will always be called from inside the channel's event loop, so we don't have to worry about
        // synchronization.
        final Promise<PushNotificationResponse<T>> responsePromise = this.responsePromises.remove(response.getPushNotification());

        if (EXPIRED_AUTH_TOKEN_REASON.equals(response.getRejectionReason())) {
            this.sendNotification(response.getPushNotification(), responsePromise);
        } else {
            responsePromise.setSuccess(response);
        }
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
