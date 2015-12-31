package com.relayrides.pushy.apns;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.GenericFutureListener;

class ApnsClientHandler<T extends ApnsPushNotification> extends Http2ConnectionHandler {

    private int nextStreamId = 1;

    private final Map<Integer, T> pushNotificationsByStreamId = new HashMap<Integer, T>();
    private final Map<Integer, Http2Headers> headersByStreamId = new HashMap<Integer, Http2Headers>();

    private ApnsClient<T> apnsClient;

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_EXPIRATION_HEADER = new AsciiString("apns-expiration");
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");

    private static final int STREAM_ID_RESET_THRESHOLD = Integer.MAX_VALUE - 1;

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateAsSecondsSinceEpochTypeAdapter())
            .create();

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final Logger log = LoggerFactory.getLogger(ApnsClientHandler.class);

    public static class Builder<S extends ApnsPushNotification> extends BuilderBase<ApnsClientHandler<S>, Builder<S>> {

        private ApnsClient<S> apnsClient;

        public Builder<S> apnsClient(final ApnsClient<S> apnsClient) {
            this.apnsClient = apnsClient;
            return this;
        }

        public ApnsClient<S> apnsClient() {
            return this.apnsClient;
        }

        @Override
        public ApnsClientHandler<S> build0(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder) {
            final ApnsClientHandler<S> handler = new ApnsClientHandler<S>(decoder, encoder, this.initialSettings(), this.apnsClient());
            this.frameListener(handler.new ApnsClientHandlerFrameAdapter());
            return handler;
        }
    }

    private class ApnsClientHandlerFrameAdapter extends Http2FrameAdapter {
        @Override
        public int onDataRead(final ChannelHandlerContext context, final int streamId, final ByteBuf data, final int padding, final boolean endOfStream) throws Http2Exception {
            final int bytesProcessed = data.readableBytes() + padding;

            if (endOfStream) {
                final Http2Headers headers = ApnsClientHandler.this.headersByStreamId.remove(streamId);
                final T pushNotification = ApnsClientHandler.this.pushNotificationsByStreamId.remove(streamId);

                final boolean success = HttpResponseStatus.OK.equals(HttpResponseStatus.parseLine(headers.status()));
                final ErrorResponse errorResponse = gson.fromJson(data.toString(UTF8), ErrorResponse.class);

                ApnsClientHandler.this.apnsClient.handlePushNotificationResponse(new PushNotificationResponse<T>(
                        pushNotification, success, errorResponse.getReason(), errorResponse.getTimestamp()));
            } else {
                log.error("Gateway sent a DATA frame that was not the end of a stream.");
            }

            return bytesProcessed;
        }

        @Override
        public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int streamDependency, final short weight, final boolean exclusive, final int padding, final boolean endOfStream) throws Http2Exception {
            this.onHeadersRead(context, streamId, headers, padding, endOfStream);
        }

        @Override
        public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) throws Http2Exception {
            final boolean success = HttpResponseStatus.OK.equals(HttpResponseStatus.parseLine(headers.status()));

            if (endOfStream) {
                if (!success) {
                    log.error("Gateway sent an end-of-stream HEADERS frame for an unsuccessful notification.");
                }
                final T pushNotification = ApnsClientHandler.this.pushNotificationsByStreamId.remove(streamId);
                assert pushNotification != null;

                ApnsClientHandler.this.apnsClient.handlePushNotificationResponse(new PushNotificationResponse<T>(
                        pushNotification, success, null, null));
            } else {
                ApnsClientHandler.this.headersByStreamId.put(streamId, headers);
            }
        }
    }

    protected ApnsClientHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final ApnsClient<T> apnsClient) {
        super(decoder, encoder, initialSettings);

        this.apnsClient = apnsClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(final ChannelHandlerContext context, final Object message, final ChannelPromise promise) {
        try {
            // We'll catch class cast issues gracefully
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
            // This should never happen, but in case some foreign debris winds up in the pipeline, just pass it through.
            log.error("Unexpected object in pipeline: {}", message);
            context.write(message, promise);
        }
    }
}
