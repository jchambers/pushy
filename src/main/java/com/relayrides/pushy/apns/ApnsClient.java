package com.relayrides.pushy.apns;

import java.util.IdentityHashMap;
import java.util.concurrent.TimeUnit;

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
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.FailedFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.SucceededFuture;

public class ApnsClient<T extends ApnsPushNotification> {

    private final Bootstrap bootstrap;
    private ChannelPromise connectionReadyPromise;

    private boolean shouldReconnect = false;
    private long reconnectDelay = INITIAL_RECONNECT_DELAY;

    private final IdentityHashMap<T, Promise<PushNotificationResponse<T>>> responsePromises =
            new IdentityHashMap<T, Promise<PushNotificationResponse<T>>>();

    private static final long INITIAL_RECONNECT_DELAY = 1; // second
    private static final long MAX_RECONNECT_DELAY = 60; // seconds

    private static final Logger log = LoggerFactory.getLogger(ApnsClient.class);

    public ApnsClient(final SslContext sslContext, final EventLoopGroup eventLoopGroup) {
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
        final Promise<PushNotificationResponse<T>> promise =
                this.responsePromises.remove(response.getPushNotification());

        promise.setSuccess(response);
    }

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
