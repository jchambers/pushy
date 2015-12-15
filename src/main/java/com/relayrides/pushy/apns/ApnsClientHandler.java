package com.relayrides.pushy.apns;

import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseAggregator;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.GenericFutureListener;

class ApnsClientHandler<T extends ApnsPushNotification> extends Http2ConnectionHandler implements Http2FrameListener {

    private int nextStreamId = 1;

    private final ApnsClient<T> client;

    private final Map<Integer, T> pushNotificationsByStreamId = new HashMap<>();
    private final Map<Integer, Http2Headers> headersByStreamId = new HashMap<>();

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_EXPIRATION_HEADER = new AsciiString("apns-expiration");

    public static final class Builder<S extends ApnsPushNotification> extends BuilderBase<ApnsClientHandler<S>, Builder<S>> {
        private ApnsClient<S> client;

        public Builder<S> client(final ApnsClient<S> client) {
            this.client = client;
            return Builder.this;
        }

        public ApnsClient<S> client() {
            return this.client;
        }

        @Override
        public ApnsClientHandler<S> build0(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder) {
            final ApnsClientHandler<S> handler = new ApnsClientHandler<S>(decoder, encoder, this.initialSettings(), this.client());
            this.frameListener(handler);
            return handler;
        }
    }

    protected ApnsClientHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final ApnsClient<T> client) {
        super(decoder, encoder, initialSettings);

        this.client = client;
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

            this.client.handlePushNotificationResponse(new PushNotificationResponse<T>(pushNotification, success));
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

            this.client.handlePushNotificationResponse(new PushNotificationResponse<T>(pushNotification, success));
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
        this.client.handleSettingsReceived();
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
