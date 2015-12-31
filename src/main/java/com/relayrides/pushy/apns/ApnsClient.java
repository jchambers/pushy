package com.relayrides.pushy.apns;

import java.io.File;
import java.security.PrivateKey;
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
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.FailedFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.SucceededFuture;

/**
 * An APNs client sends push notifications to the APNs gateway.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @param <T> the type of notification handled by the client
 */
public class ApnsClient<T extends ApnsPushNotification> {

    private final Bootstrap bootstrap;
    private ChannelPromise connectionReadyPromise;

    private boolean shouldReconnect = false;
    private long reconnectDelay = INITIAL_RECONNECT_DELAY;

    private final Map<T, Promise<PushNotificationResponse<T>>> responsePromises =
            new IdentityHashMap<T, Promise<PushNotificationResponse<T>>>();

    private static final long INITIAL_RECONNECT_DELAY = 1; // second
    private static final long MAX_RECONNECT_DELAY = 60; // seconds

    private static final Logger log = LoggerFactory.getLogger(ApnsClient.class);

    /**
     * Creates a new APNs client that will identify itself to the APNs gateway with the certificate and key from the
     * given files. The certificate file <em>must</em> contain a PEM-formatted X.509 certificate, and the key file
     * <em>must</em> contain a PKCS8-formatted private key.
     *
     * @param certificatePemFile a PEM-formatted file containing an X.509 certificate to be used to identify the client
     * to the APNs server
     * @param privateKeyPkcs8File a PKCS8-formatted file containing a private key for the client certificate
     * @param privateKeyPassword the password to be used to decrypt the private key; may be {@code null} if the private
     * key does not require a password
     * @param eventLoopGroup TODO
     *
     * @throws SSLException if the given key or certificate could not be loaded or if any other SSL-related problem
     * arises when constructing the context
     */
    public ApnsClient(final File certificatePemFile, final File privateKeyPkcs8File, final String privateKeyPassword, final EventLoopGroup eventLoopGroup) throws SSLException {
        this(ApnsClient.getBaseSslContextBuilder()
                .keyManager(certificatePemFile, privateKeyPkcs8File, privateKeyPassword)
                .build(),
                eventLoopGroup);
    }

    /**
     * Creates a new APNs client that will identify itself to the APNs gateway with the given certificate and key.
     *
     * @param certificate the certificate to be used to identify the client to the APNs server
     * @param privateKey the private key for the client certificate
     * @param privateKeyPassword the password to be used to decrypt the private key; may be {@code null} if the private
     * key does not require a password
     * @param eventLoopGroup TODO
     *
     * @throws SSLException if the given key or certificate could not be loaded or if any other SSL-related problem
     * arises when constructing the context
     */
    public ApnsClient(final X509Certificate certificate, final PrivateKey privateKey, final String privateKeyPassword, final EventLoopGroup eventLoopGroup) throws SSLException {
        this(ApnsClient.getBaseSslContextBuilder()
                .keyManager(privateKey, privateKeyPassword, certificate)
                .build(),
                eventLoopGroup);
    }

    private static SslContextBuilder getBaseSslContextBuilder() {
        return SslContextBuilder.forClient()
                .sslProvider(OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .applicationProtocolConfig(
                        new ApplicationProtocolConfig(Protocol.ALPN,
                                SelectorFailureBehavior.NO_ADVERTISE,
                                SelectedListenerFailureBehavior.ACCEPT,
                                ApplicationProtocolNames.HTTP_2));
    }

    protected ApnsClient(final SslContext sslContext, final EventLoopGroup eventLoopGroup) {
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(eventLoopGroup);
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
                            context.pipeline().addLast(new ApnsClientHandler.Builder<T>()
                                    .server(false)
                                    .apnsClient(ApnsClient.this)
                                    .encoderEnforceMaxConcurrentStreams(true)
                                    .build());

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
     * Connects to an APNs gateway at the given address.
     *
     * @param host
     * @param port
     *
     * @return a {@code Future} that will be complete when a secure connection to the APNs gateway has been established
     */
    public Future<Void> connect(final String host, final int port) {
        synchronized (this.bootstrap) {
            // We only want to begin a connection attempt if one is not already in progress or complete; if we already
            // have a connection future, just return the existing promise.
            if (this.connectionReadyPromise == null) {
                final ChannelFuture connectFuture = this.bootstrap.connect(host, port);
                this.connectionReadyPromise = connectFuture.channel().newPromise();

                connectFuture.addListener(new GenericFutureListener<ChannelFuture>() {

                    @Override
                    public void operationComplete(final ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            ApnsClient.this.connectionReadyPromise.tryFailure(future.cause());
                        }
                    }
                });

                connectFuture.channel().closeFuture().addListener(new GenericFutureListener<ChannelFuture> () {

                    @Override
                    public void operationComplete(final ChannelFuture future) throws Exception {
                        // We always want to try to fail the "connection ready" promise if the connection closes; if
                        // it has already succeeded, this will have no effect.
                        ApnsClient.this.connectionReadyPromise.tryFailure(
                                new IllegalStateException("Channel closed before HTTP/2 preface completed."));

                        synchronized (ApnsClient.this.bootstrap) {
                            ApnsClient.this.connectionReadyPromise = null;

                            if (ApnsClient.this.shouldReconnect) {
                                future.channel().eventLoop().schedule(new Runnable() {

                                    @Override
                                    public void run() {
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
                                ApnsClient.this.reconnectDelay = INITIAL_RECONNECT_DELAY;
                                ApnsClient.this.shouldReconnect = true;
                            }
                        }
                    }});
            }

            return this.connectionReadyPromise;
        }
    }

    public boolean isConnected() {
        synchronized (this.bootstrap) {
            return this.connectionReadyPromise != null && this.connectionReadyPromise.isSuccess();
        }
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
     * non-permanent, and callers should attempt to re-send the notification when the underlying problem (e.g. a
     * connection failure) has been resolved.</p>
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
                    new DefaultPromise<PushNotificationResponse<T>>(connectionReadyPromise.channel().eventLoop());

            this.responsePromises.put(notification, responsePromise);

            connectionReadyPromise.channel().writeAndFlush(notification).addListener(new GenericFutureListener<ChannelFuture>() {

                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        ApnsClient.this.responsePromises.remove(notification);
                        responsePromise.setFailure(future.cause());
                    }
                }
            });

            responseFuture = responsePromise;
        } else {
            responseFuture = new FailedFuture<PushNotificationResponse<T>>(
                    GlobalEventExecutor.INSTANCE, new IllegalStateException("Channel is not active"));
        }

        return responseFuture;
    }

    protected void handlePushNotificationResponse(final PushNotificationResponse<T> response) {
        this.responsePromises.remove(response.getPushNotification()).setSuccess(response);
    }

    // TODO Expose graceful shutdown timeout methods

    /**
     * Gracefully disconnects from the APNs gateway. The disconnection process will wait until notifications in flight
     * have been either accepted or rejected by the gateway. The returned {@code Future} will be marked as complete
     * when the connection has closed completely. If the connection is already closed when this method is called, the
     * returned {@code Future} will be marked as complete immediately.
     *
     * @return a {@code Future} that will be marked as complete when the connection has been closed
     */
    public Future<Void> disconnect() {
        final Future<Void> disconnectFuture;

        synchronized (this.bootstrap) {
            this.shouldReconnect = false;

            if (this.connectionReadyPromise != null) {
                disconnectFuture = this.connectionReadyPromise.channel().close();
            } else {
                disconnectFuture = new SucceededFuture<Void>(GlobalEventExecutor.INSTANCE, null);
            }
        }

        return disconnectFuture;
    }
}
