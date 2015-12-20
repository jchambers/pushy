package com.relayrides.pushy.apns;

import static io.netty.handler.logging.LogLevel.INFO;

import java.io.Closeable;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.concurrent.Future;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;

public class ApnsClient<T extends ApnsPushNotification> implements Closeable {

    static final AttributeKey<ChannelPromise> PREFACE_PROMISE_KEY = AttributeKey.newInstance("pushyPrefacePromise");

    private final EventLoopGroup eventLoopGroup;
    private final Bootstrap bootstrap;

    private Channel channel;

    private final IdentityHashMap<T, Promise<PushNotificationResponse<T>>> responsePromises = new IdentityHashMap<>();

    public ApnsClient(final SslContext sslContext, final EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;

        this.bootstrap = new Bootstrap();
        this.bootstrap.group(this.eventLoopGroup);
        this.bootstrap.channel(NioSocketChannel.class);
        this.bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
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
                                    .frameLogger(new Http2FrameLogger(INFO, ApnsClient.class))
                                    .server(false)
                                    .encoderEnforceMaxConcurrentStreams(true)
                                    .client(ApnsClient.this)
                                    .build());
                        } else {
                            context.close();
                            throw new IllegalStateException("Unexpected protocol: " + protocol);
                        }
                    }

                    @Override
                    protected void handshakeFailure(final ChannelHandlerContext context, final Throwable cause) throws Exception {
                        super.handshakeFailure(context, cause);
                        context.channel().attr(PREFACE_PROMISE_KEY).get().tryFailure(cause);
                    }
                });
            }
        });
    }

    public Future<Void> connect(final String hostname, final int port) {
        final ChannelFuture connectFuture = this.bootstrap.connect(hostname, port);
        final ChannelPromise prefacePromise = connectFuture.channel().newPromise();

        connectFuture.channel().attr(PREFACE_PROMISE_KEY).set(prefacePromise);

        connectFuture.addListener(new GenericFutureListener<ChannelFuture>() {

            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    future.channel().attr(PREFACE_PROMISE_KEY).get().tryFailure(future.cause());
                }
            }
        });

        connectFuture.channel().closeFuture().addListener(new GenericFutureListener<ChannelFuture> () {

            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                future.channel().attr(PREFACE_PROMISE_KEY).get().tryFailure(
                        new IllegalStateException("Channel closed before HTTP/2 preface completed."));

                // TODO Try to reconnect if appropriate
            }
        });

        prefacePromise.addListener(new GenericFutureListener<ChannelFuture> () {

            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    ApnsClient.this.channel = future.channel();
                } else {
                    ApnsClient.this.channel = null;
                }
            }
        });

        return prefacePromise;
    }

    public Future<PushNotificationResponse<T>> sendNotification(final T notification) {

        // TODO Make sure we actually have a channel first
        final DefaultPromise<PushNotificationResponse<T>> responsePromise =
                new DefaultPromise<>(this.channel.eventLoop());

        this.responsePromises.put(notification, responsePromise);

        this.channel.writeAndFlush(notification).addListener(new GenericFutureListener<ChannelFuture>() {

            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    ApnsClient.this.responsePromises.remove(notification);
                    responsePromise.setFailure(future.cause());
                }
            }
        });

        // TODO
        return responsePromise;
    }

    protected void handlePushNotificationResponse(final PushNotificationResponse<T> response) {
        final Promise<PushNotificationResponse<T>> promise =
                this.responsePromises.remove(response.getPushNotification());

        assert promise != null;

        promise.setSuccess(response);
    }

    @Override
    public void close() throws IOException {
        // TODO Cancel in-progress connection attempts
        // TODO Synchronize everything
        if (this.channel != null) {
            try {
                this.channel.close().await();
            } catch (final InterruptedException e) {
                throw new IOException(e);
            }
        }
    }
}
