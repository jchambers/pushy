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

import java.io.File;
import java.io.IOException;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
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
 * <p>To construct a client, callers will need to provide the certificate provisioned by Apple and its accompanying
 * private key. The certificate and key will be used to authenticate the client and identify the topics to which it can
 * send notifications. Callers may optionally specify an {@link NioEventLoopGroup} when constructing a new client. If no
 * event loop group is specified, clients will create and manage their own single-thread event loop group. If many
 * clients are operating in parallel, specifying a shared event loop group serves as a mechanism to keep the total
 * number of threads in check.</p>
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
 */
public class ApnsClient<T extends ApnsPushNotification> {

    private final Bootstrap bootstrap;
    private final boolean shouldShutDownEventLoopGroup;

    private Long gracefulShutdownTimeoutMillis;

    private volatile ChannelPromise connectionReadyPromise;
    private volatile ChannelPromise reconnectionPromise;
    private long reconnectDelay = INITIAL_RECONNECT_DELAY;

    private final Map<T, Promise<PushNotificationResponse<T>>> responsePromises = new IdentityHashMap<>();

    /**
     * The hostname for the production APNs gateway.
     */
    public static final String PRODUCTION_APNS_HOST = "api.push.apple.com";

    /**
     * The hostname for the development APNs gateway.
     */
    public static final String DEVELOPMENT_APNS_HOST = "api.development.push.apple.com";

    /**
     * The default (HTTPS) port for communication with the APNs gateway.
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
     */
    public static final int ALTERNATE_APNS_PORT = 2197;

    private static final ClientNotConnectedException NOT_CONNECTED_EXCEPTION = new ClientNotConnectedException();

    private static final long INITIAL_RECONNECT_DELAY = 1; // second
    private static final long MAX_RECONNECT_DELAY = 60; // seconds

    private static final Logger log = LoggerFactory.getLogger(ApnsClient.class);

    /**
     * <p>Creates a new APNs client that will identify itself to the APNs gateway with the certificate and key from the
     * given PKCS#12 file. The PKCS#12 file <em>must</em> contain a single certificate/private key pair.</p>
     *
     * <p>Clients created using this method will use a default, internally-managed event loop group. Once the client
     * has been disconnected via the {@link ApnsClient#disconnect()} method, its event loop will be shut down and the
     * client cannot be reconnected.</p>
     *
     * @param p12File a PKCS#12-formatted file containing a the certificate and private key to be used to identify the
     * client to the APNs server
     * @param password the password to be used to decrypt the contents of the given PKCS#12 file
     *
     * @throws SSLException if the given PKCS#12 file could not be loaded or if any other SSL-related problem arises
     * when constructing the context
     */
    public ApnsClient(final File p12File, final String password) throws SSLException {
        this(p12File, password, null);
    }

    /**
     * <p>Creates a new APNs client that will identify itself to the APNs gateway with the certificate and key from the
     * given PKCS#12 file. The PKCS#12 file <em>must</em> contain a single certificate/private key pair.</p>
     *
     * <p>Clients created using this method will use the provided event loop group, which may be useful in cases where
     * multiple clients are running in parallel. If a client is created using this method, it is the responsibility of
     * the caller to shut down the event loop group. Clients created with an externally-provided event loop group may be
     * reconnected after being shut down via the {@link ApnsClient#disconnect()} method.</p>
     *
     * @param p12File a PKCS#12-formatted file containing a the certificate and private key to be used to identify the
     * client to the APNs server
     * @param password the password to be used to decrypt the contents of the given PKCS#12 file
     * @param eventLoopGroup the event loop group to be used to handle I/O events for this client
     *
     * @throws SSLException if the given PKCS#12 file could not be loaded or if any other SSL-related problem arises
     * when constructing the context
     */
    public ApnsClient(final File p12File, final String password, final NioEventLoopGroup eventLoopGroup) throws SSLException {
        this(ApnsClient.getSslContextWithP12File(p12File, password), eventLoopGroup);
    }

    /**
     * <p>Creates a new APNs client that will identify itself to the APNs gateway with the given certificate and
     * key.</p>
     *
     * <p>Clients created using this method will use a default, internally-managed event loop group. Once the client
     * has been disconnected via the {@link ApnsClient#disconnect()} method, its event loop will be shut down and the
     * client cannot be reconnected.</p>
     *
     * @param certificate the certificate to be used to identify the client to the APNs server
     * @param privateKey the private key for the client certificate
     * @param privateKeyPassword the password to be used to decrypt the private key; may be {@code null} if the private
     * key does not require a password
     *
     * @throws SSLException if the given key or certificate could not be loaded or if any other SSL-related problem
     * arises when constructing the context
     */
    public ApnsClient(final X509Certificate certificate, final PrivateKey privateKey, final String privateKeyPassword) throws SSLException {
        this(certificate, privateKey, privateKeyPassword, null);
    }

    /**
     * <p>Creates a new APNs client that will identify itself to the APNs gateway with the given certificate and
     * key.</p>
     *
     * <p>Clients created using this method will use the provided event loop group, which may be useful in cases where
     * multiple clients are running in parallel. If a client is created using this method, it is the responsibility of
     * the caller to shut down the event loop group. Clients created with an externally-provided event loop group may be
     * reconnected after being shut down via the {@link ApnsClient#disconnect()} method.</p>
     *
     * @param certificate the certificate to be used to identify the client to the APNs server
     * @param privateKey the private key for the client certificate
     * @param privateKeyPassword the password to be used to decrypt the private key; may be {@code null} if the private
     * key does not require a password
     * @param eventLoopGroup the event loop group to be used to handle I/O events for this client
     *
     * @throws SSLException if the given key or certificate could not be loaded or if any other SSL-related problem
     * arises when constructing the context
     */
    public ApnsClient(final X509Certificate certificate, final PrivateKey privateKey, final String privateKeyPassword, final NioEventLoopGroup eventLoopGroup) throws SSLException {
        this(ApnsClient.getSslContextWithCertificateAndPrivateKey(certificate, privateKey, privateKeyPassword), eventLoopGroup);
    }

    private static SslContext getSslContextWithP12File(final File p12File, final String password) throws SSLException {
        final X509Certificate x509Certificate;
        final PrivateKey privateKey;

        try {
            final PrivateKeyEntry privateKeyEntry = P12Util.getPrivateKeyEntryFromP12File(p12File, password);

            final Certificate certificate = privateKeyEntry.getCertificate();

            if (!(certificate instanceof X509Certificate)) {
                throw new KeyStoreException("Found a certificate in the provided PKCS#12 file, but it was not an X.509 certificate.");
            }

            x509Certificate = (X509Certificate) certificate;
            privateKey = privateKeyEntry.getPrivateKey();
        } catch (final KeyStoreException e) {
            throw new SSLException(e);
        } catch (final NoSuchAlgorithmException e) {
            throw new SSLException(e);
        } catch (final UnrecoverableEntryException e) {
            throw new SSLException(e);
        } catch (final CertificateException e) {
            throw new SSLException(e);
        } catch (final IOException e) {
            throw new SSLException(e);
        }

        return ApnsClient.getSslContextWithCertificateAndPrivateKey(x509Certificate, privateKey, password);
    }

    private static SslContext getSslContextWithCertificateAndPrivateKey(final X509Certificate certificate, final PrivateKey privateKey, final String privateKeyPassword) throws SSLException {
        return ApnsClient.getBaseSslContextBuilder()
                .keyManager(privateKey, privateKeyPassword, certificate)
                .build();
    }

    private static SslContextBuilder getBaseSslContextBuilder() {
        final SslProvider sslProvider;

        if (OpenSsl.isAvailable()) {
            if (OpenSsl.isAlpnSupported()) {
                log.info("OpenSSL (via netty-tcnative) is available and supports ALPN; will use OpenSSL.");
                sslProvider = SslProvider.OPENSSL;
            } else {
                log.info("OpenSSL (via netty-tcnative) is available, but does not support ALPN; will use JDK SSL provider.");
                sslProvider = SslProvider.JDK;
            }
        } else {
            log.info("OpenSSL (via netty-tcnative) not available; will use JDK SSL provider.");
            sslProvider = SslProvider.JDK;
        }

        return SslContextBuilder.forClient()
                .sslProvider(sslProvider)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .applicationProtocolConfig(
                        new ApplicationProtocolConfig(Protocol.ALPN,
                                SelectorFailureBehavior.NO_ADVERTISE,
                                SelectedListenerFailureBehavior.ACCEPT,
                                ApplicationProtocolNames.HTTP_2));
    }

    protected ApnsClient(final SslContext sslContext, final NioEventLoopGroup eventLoopGroup) {
        this.bootstrap = new Bootstrap();

        if (eventLoopGroup != null) {
            this.bootstrap.group(eventLoopGroup);
            this.shouldShutDownEventLoopGroup = false;
        } else {
            this.bootstrap.group(new NioEventLoopGroup(1));
            this.shouldShutDownEventLoopGroup = true;
        }

        this.bootstrap.channel(NioSocketChannel.class);
        this.bootstrap.option(ChannelOption.TCP_NODELAY, true);
        this.bootstrap.handler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel) throws Exception {
                final ChannelPipeline pipeline = channel.pipeline();
                pipeline.addLast(sslContext.newHandler(channel.alloc()));
                pipeline.addLast(new ApplicationProtocolNegotiationHandler("") {
                    @Override
                    protected void configurePipeline(final ChannelHandlerContext context, final String protocol) {
                        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                            final ApnsClientHandler<T> apnsClientHandler = new ApnsClientHandler.Builder<T>()
                                    .server(false)
                                    .apnsClient(ApnsClient.this)
                                    .encoderEnforceMaxConcurrentStreams(true)
                                    .build();

                            synchronized (ApnsClient.this.bootstrap) {
                                if (ApnsClient.this.gracefulShutdownTimeoutMillis != null) {
                                    apnsClientHandler.gracefulShutdownTimeoutMillis(ApnsClient.this.gracefulShutdownTimeoutMillis);
                                }
                            }

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
                        super.handshakeFailure(context, cause);

                        final ChannelPromise connectionReadyPromise = ApnsClient.this.connectionReadyPromise;

                        if (connectionReadyPromise != null) {
                            connectionReadyPromise.tryFailure(cause);
                        }
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
     */
    public void setConnectionTimeout(final int timeoutMillis) {
        synchronized (this.bootstrap) {
            this.bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMillis);
        }
    }

    /**
     * <p>Connects to the given APNs gateway on the default (HTTPS) port ({@value DEFAULT_APNS_PORT}).</p>
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
     */
    public Future<Void> connect(final String host, final int port) {
        final Future<Void> connectionReadyFuture;

        if (this.bootstrap.group().isShuttingDown() || this.bootstrap.group().isShutdown()) {
            connectionReadyFuture = new FailedFuture<>(GlobalEventExecutor.INSTANCE,
                    new IllegalStateException("Client's event loop group has been shut down and cannot be restarted."));
        } else {
            synchronized (this.bootstrap) {
                // We only want to begin a connection attempt if one is not already in progress or complete; if we already
                // have a connection future, just return the existing promise.
                if (this.connectionReadyPromise == null) {
                    final ChannelFuture connectFuture = this.bootstrap.connect(host, port);
                    this.connectionReadyPromise = connectFuture.channel().newPromise();

                    connectFuture.channel().closeFuture().addListener(new GenericFutureListener<ChannelFuture> () {

                        @Override
                        public void operationComplete(final ChannelFuture future) throws Exception {
                            // We always want to try to fail the "connection ready" promise if the connection closes; if
                            // it has already succeeded, this will have no effect.
                            ApnsClient.this.connectionReadyPromise.tryFailure(
                                    new IllegalStateException("Channel closed before HTTP/2 preface completed."));

                            synchronized (ApnsClient.this.bootstrap) {
                                ApnsClient.this.connectionReadyPromise = null;

                                if (ApnsClient.this.reconnectionPromise != null) {
                                    log.debug("Disconnected. Next automatic reconnection attempt in {} seconds.", ApnsClient.this.reconnectDelay);

                                    future.channel().eventLoop().schedule(new Runnable() {

                                        @Override
                                        public void run() {
                                            log.debug("Attempting to reconnect.");
                                            ApnsClient.this.connect(host, port);
                                        }
                                    }, ApnsClient.this.reconnectDelay, TimeUnit.SECONDS);

                                    ApnsClient.this.reconnectDelay = Math.min(ApnsClient.this.reconnectDelay, MAX_RECONNECT_DELAY);
                                }
                            }
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

                                    ApnsClient.this.reconnectDelay = INITIAL_RECONNECT_DELAY;
                                    ApnsClient.this.reconnectionPromise = future.channel().newPromise();
                                }
                            } else {
                                log.info("Failed to connect.", future.cause());
                            }
                        }});
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
     * @return a {@code Future} that will complete when the notification has been either accepted or rejected by the
     * APNs gateway
     */
    public Future<PushNotificationResponse<T>> sendNotification(final T notification) {

        final Future<PushNotificationResponse<T>> responseFuture;

        // Instead of synchronizing here, we keep a final reference to the connection ready promise. We can get away
        // with this because we're not changing the state of the connection or its promises. Keeping a reference ensures
        // we won't suddenly "lose" the channel and get a NullPointerException, but risks sending a notification after
        // things have shut down. In that case, though, the returned futures should fail quickly, and the benefit of
        // not synchronizing for every write seems worth it.
        final ChannelPromise connectionReadyPromise = this.connectionReadyPromise;

        if (connectionReadyPromise != null && connectionReadyPromise.isSuccess() && connectionReadyPromise.channel().isActive()) {
            final DefaultPromise<PushNotificationResponse<T>> responsePromise =
                    new DefaultPromise<>(connectionReadyPromise.channel().eventLoop());

            connectionReadyPromise.channel().eventLoop().submit(new Runnable() {

                @Override
                public void run() {
                    // We want to do this inside the channel's event loop so we can be sure that only one thread is
                    // modifying responsePromises.
                    ApnsClient.this.responsePromises.put(notification, responsePromise);
                }
            });

            connectionReadyPromise.channel().writeAndFlush(notification).addListener(new GenericFutureListener<ChannelFuture>() {

                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        log.debug("Failed to write push notification: {}", notification, future.cause());

                        // This will always be called from inside the channel's event loop, so we don't have to worry
                        // about synchronization.
                        ApnsClient.this.responsePromises.remove(notification);
                        responsePromise.setFailure(future.cause());
                    }
                }
            });

            responseFuture = responsePromise;
        } else {
            log.debug("Failed to send push notification because client is not connected: {}", notification);
            responseFuture = new FailedFuture<>(
                    GlobalEventExecutor.INSTANCE, NOT_CONNECTED_EXCEPTION);
        }

        return responseFuture;
    }

    protected void handlePushNotificationResponse(final PushNotificationResponse<T> response) {
        log.debug("Received response from APNs gateway: {}", response);

        // This will always be called from inside the channel's event loop, so we don't have to worry about
        // synchronization.
        this.responsePromises.remove(response.getPushNotification()).setSuccess(response);
    }

    /**
     * Sets the amount of time (in milliseconds) clients should wait for in-progress requests to complete before closing
     * a connection during a graceful shutdown.
     *
     * @param timeoutMillis the number of milliseconds to wait for in-progress requests to complete before closing a
     * connection
     *
     * @see ApnsClient#disconnect()
     */
    public void setGracefulShutdownTimeout(final long timeoutMillis) {
        synchronized (this.bootstrap) {
            this.gracefulShutdownTimeoutMillis = timeoutMillis;

            if (this.connectionReadyPromise != null) {
                @SuppressWarnings("rawtypes")
                final ApnsClientHandler handler =
                this.connectionReadyPromise.channel().pipeline().get(ApnsClientHandler.class);

                if (handler != null) {
                    handler.gracefulShutdownTimeoutMillis(timeoutMillis);
                }
            }
        }
    }

    /**
     * <p>Gracefully disconnects from the APNs gateway. The disconnection process will wait until notifications in
     * flight have been either accepted or rejected by the gateway. The returned {@code Future} will be marked as
     * complete when the connection has closed completely. If the connection is already closed when this method is
     * called, the returned {@code Future} will be marked as complete immediately.</p>
     *
     * <p>If a non-null {@code EventLoopGroup} was provided at construction time, clients may be reconnected and reused
     * after they have been disconnected. If no event loop group was provided at construction time, clients may not be
     * restarted after they have been disconnected via this method.</p>
     *
     * @return a {@code Future} that will be marked as complete when the connection has been closed
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
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
                        ApnsClient.this.bootstrap.group().shutdownGracefully();
                    }
                });

                // Since the termination future for the event loop group is a Future<?> instead of a Future<Void>,
                // we'll need to create our own promise and then notify it when the termination future completes.
                disconnectFuture = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);

                this.bootstrap.group().terminationFuture().addListener(new GenericFutureListener() {

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
