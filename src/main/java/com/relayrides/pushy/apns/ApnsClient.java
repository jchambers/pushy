package com.relayrides.pushy.apns;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AsciiString;
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

    private final IdentityHashMap<T, Promise<PushNotificationResponse<T>>> responsePromises = new IdentityHashMap<>();

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_EXPIRATION_HEADER = new AsciiString("apns-expiration");
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");

    private static final long INITIAL_RECONNECT_DELAY = 1; // second
    private static final long MAX_RECONNECT_DELAY = 60; // seconds

    private static final int STREAM_ID_RESET_THRESHOLD = Integer.MAX_VALUE - 1;

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateAsSecondsSinceEpochTypeAdapter())
            .create();

    private static final Logger log = LoggerFactory.getLogger(ApnsClient.class);

    private static final Charset UTF8 = Charset.forName("UTF-8");

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

                final boolean success = HttpResponseStatus.OK.equals(HttpResponseStatus.parseLine(headers.status()));
                final ErrorResponse errorResponse = gson.fromJson(data.toString(UTF8), ErrorResponse.class);

                ApnsClient.this.handlePushNotificationResponse(new PushNotificationResponse<T>(
                        pushNotification, success, errorResponse.getReason(), errorResponse.getTimestamp()));
            } else {
                log.error("Gateway sent a DATA frame that was not the end of a stream.");
            }

            return bytesProcessed;
        }

        @Override
        public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) throws Http2Exception {
            final boolean success = HttpResponseStatus.OK.equals(HttpResponseStatus.parseLine(headers.status()));

            if (endOfStream) {
                if (!success) {
                    log.error("Gateway sent an end-of-stream HEADERS frame for an unsuccessful notification.");
                }
                final T pushNotification = this.pushNotificationsByStreamId.remove(streamId);
                assert pushNotification != null;

                ApnsClient.this.handlePushNotificationResponse(new PushNotificationResponse<T>(
                        pushNotification, success, null, null));
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
            // Always try to notify the "connection is ready" promise; if it's already been notified, this will have no
            // effect.
            ApnsClient.this.connectionReadyPromise.trySuccess();
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

                final byte[] payloadBytes = pushNotification.getPayload().getBytes(UTF8);

                final Http2Headers headers = new DefaultHttp2Headers()
                        .method("POST")
                        .path(APNS_PATH_PREFIX + pushNotification.getToken())
                        .addInt(HttpHeaderNames.CONTENT_LENGTH, payloadBytes.length)
                        .addInt(APNS_EXPIRATION_HEADER, pushNotification.getExpiration() == null ? 0 : (int) (pushNotification.getExpiration().getTime() / 1000));

                if (pushNotification.getTopic() != null) {
                    headers.add(APNS_TOPIC_HEADER, pushNotification.getTopic());
                }

                final ChannelPromise headersPromise = context.newPromise();
                this.encoder().writeHeaders(context, streamId, headers, 0, false, headersPromise);

                final ChannelPromise dataPromise = context.newPromise();
                this.encoder().writeData(context, streamId, Unpooled.wrappedBuffer(payloadBytes), 0, true, dataPromise);

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

                this.nextStreamId += 2;

                if (this.nextStreamId >= STREAM_ID_RESET_THRESHOLD) {
                    // This is very unlikely, but in the event that we run out of stream IDs (the maximum allowed is
                    // 2^31, per https://httpwg.github.io/specs/rfc7540.html#StreamIdentifiers), we need to open a new
                    // connection. Just closing the context should be enough; automatic reconnection should take things
                    // from there.
                    context.close();
                }

            } catch (final ClassCastException e) {
                // This should never happen, but in case some foreign debris winds up in the pipeline, just pass it
                // through.
                log.error("Unexpected object in pipeline: {}", message);
                context.write(message, promise);
            }
        }
    }

    public ApnsClient(final SslContext sslContext, final EventLoopGroup eventLoopGroup) {
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(eventLoopGroup);
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
                    new DefaultPromise<>(connectionReadyPromise.channel().eventLoop());

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
            responseFuture = new FailedFuture<>(GlobalEventExecutor.INSTANCE, new IllegalStateException("Channel is not active"));
        }

        return responseFuture;
    }

    protected void handlePushNotificationResponse(final PushNotificationResponse<T> response) {
        final Promise<PushNotificationResponse<T>> promise =
                this.responsePromises.remove(response.getPushNotification());

        assert promise != null;

        promise.setSuccess(response);
    }

    public Future<Void> disconnect() {
        final Future<Void> disconnectFuture;

        synchronized (this.bootstrap) {
            this.shouldReconnect = false;

            if (this.connectionReadyPromise != null) {
                disconnectFuture = this.connectionReadyPromise.channel().close();
            } else {
                disconnectFuture = new SucceededFuture<>(GlobalEventExecutor.INSTANCE, null);
            }
        }

        return disconnectFuture;
    }
}
