package com.relayrides.pushy.apns;

import static io.netty.handler.logging.LogLevel.INFO;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Future;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseAggregator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ConnectionHandler.BuilderBase;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AsciiString;
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

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_EXPIRATION_HEADER = new AsciiString("apns-expiration");

    private class ApnsClientHandlerBuilder extends BuilderBase<ApnsClientHandler, ApnsClientHandlerBuilder> {
        @Override
        public ApnsClientHandler build0(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder) {
            final ApnsClientHandler handler = new ApnsClientHandler(decoder, encoder, this.initialSettings());
            this.frameListener(handler);
            return handler;
        }
    }

    private class ApnsClientHandler extends Http2ConnectionHandler implements Http2FrameListener {

        private int nextStreamId = 1;

        private final Map<Integer, T> pushNotificationsByStreamId = new HashMap<>();
        private final Map<Integer, Http2Headers> headersByStreamId = new HashMap<>();

        protected ApnsClientHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
            super(decoder, encoder, initialSettings);
        }

        @Override
        public int onDataRead(final ChannelHandlerContext context, final int streamId, final ByteBuf data, final int padding, final boolean endOfStream) throws Http2Exception {
            final int bytesProcessed = data.readableBytes() + padding;

            if (endOfStream) {
                final Http2Headers headers = this.headersByStreamId.remove(streamId);
                final T pushNotification = this.pushNotificationsByStreamId.remove(streamId);

                assert headers != null;
                assert pushNotification != null;

                // TODO Actually parse the response and include it in the processed result
                final boolean success = HttpResponseStatus.OK.equals(HttpResponseStatus.parseLine(headers.status()));

                ApnsClient.this.handlePushNotificationResponse(new PushNotificationResponse<T>(pushNotification, success));
            } else {
                // TODO Complain?
            }

            return bytesProcessed;
        }

        @Override
        public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) throws Http2Exception {
            final boolean success = HttpResponseStatus.OK.equals(HttpResponseStatus.parseLine(headers.status()));

            if (endOfStream) {
                // TODO We should only get an end-of-stream with headers for successful replies; warn if not?
                final T pushNotification = this.pushNotificationsByStreamId.remove(streamId);
                assert pushNotification != null;

                ApnsClient.this.handlePushNotificationResponse(new PushNotificationResponse<T>(pushNotification, success));
            } else {
                this.headersByStreamId.put(streamId, headers);
            }
        }

        @Override
        public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int streamDependency, final short weight, final boolean exclusive, final int padding, final boolean endOfStream) throws Http2Exception {
            this.onHeadersRead(context, streamId, headers, padding, endOfStream);
        }

        @Override
        public void onPriorityRead(final ChannelHandlerContext context, final int streamId, final int streamDependency, final short weight, final boolean exclusive) throws Http2Exception {
        }

        @Override
        public void onRstStreamRead(final ChannelHandlerContext context, final int streamId, final long errorCode) throws Http2Exception {
        }

        @Override
        public void onSettingsAckRead(final ChannelHandlerContext context) throws Http2Exception {
        }

        @Override
        public void onSettingsRead(final ChannelHandlerContext context, final Http2Settings settings) throws Http2Exception {
            context.channel().attr(ApnsClient.PREFACE_PROMISE_KEY).get().trySuccess();
        }

        @Override
        public void onPingRead(final ChannelHandlerContext context, final ByteBuf data) throws Http2Exception {
        }

        @Override
        public void onPingAckRead(final ChannelHandlerContext ctx, final ByteBuf data) throws Http2Exception {
        }

        @Override
        public void onPushPromiseRead(final ChannelHandlerContext ctx, final int streamId, final int promisedStreamId, final Http2Headers headers, final int padding) throws Http2Exception {
        }

        @Override
        public void onGoAwayRead(final ChannelHandlerContext ctx, final int lastStreamId, final long errorCode, final ByteBuf debugData) throws Http2Exception {
        }

        @Override
        public void onWindowUpdateRead(final ChannelHandlerContext ctx, final int streamId, final int windowSizeIncrement) throws Http2Exception {
        }

        @Override
        public void onUnknownFrame(final ChannelHandlerContext ctx, final byte frameType, final int streamId, final Http2Flags flags, final ByteBuf payload) throws Http2Exception {
        }

        @Override
        public void write(final ChannelHandlerContext context, final Object message, final ChannelPromise promise) {
            try {
                // We'll catch class cast issues gracefully
                @SuppressWarnings("unchecked")
                final T pushNotification = (T) message;

                final int streamId = this.nextStreamId;

                // TODO Even though it's very unlikely, make sure that we don't run out of stream IDs
                this.nextStreamId += 2;

                final Http2Headers headers = new DefaultHttp2Headers()
                        .method("POST")
                        .path(APNS_PATH_PREFIX + pushNotification.getToken())
                        .addInt(HttpHeaderNames.CONTENT_LENGTH, pushNotification.getPayload().getBytes().length)
                        .addInt(APNS_EXPIRATION_HEADER, pushNotification.getExpiration() == null ? 0 : (int) (pushNotification.getExpiration().getTime() / 1000));

                final ChannelPromise headersPromise = context.newPromise();
                this.encoder().writeHeaders(context, streamId, headers, 0, false, headersPromise);

                final ChannelPromise dataPromise = context.newPromise();
                this.encoder().writeData(context, streamId, Unpooled.wrappedBuffer(pushNotification.getPayload().getBytes()), 0, true, dataPromise);

                final ChannelPromiseAggregator promiseAggregator = new ChannelPromiseAggregator(promise);
                promiseAggregator.add(headersPromise, dataPromise);

                promise.addListener(new GenericFutureListener<ChannelPromise>() {

                    @Override
                    public void operationComplete(final ChannelPromise future) throws Exception {
                        if (future.isSuccess()) {
                            ApnsClientHandler.this.pushNotificationsByStreamId.put(streamId, pushNotification);
                        }
                    }
                });
            } catch (final ClassCastException e) {
                context.write(message, promise);
            }
        }
    }

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
                            context.pipeline().addLast(new ApnsClientHandlerBuilder()
                                    .frameLogger(new Http2FrameLogger(INFO, ApnsClient.class))
                                    .server(false)
                                    .encoderEnforceMaxConcurrentStreams(true)
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
