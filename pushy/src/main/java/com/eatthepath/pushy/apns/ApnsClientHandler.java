/*
 * Copyright (c) 2020 Jon Chambers
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
 * THE SOFTWARE.
 */

package com.eatthepath.pushy.apns;

import com.eatthepath.uuid.FastUUID;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.eatthepath.pushy.apns.util.DateAsTimeSinceEpochTypeAdapter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AsciiString;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

class ApnsClientHandler extends Http2ConnectionHandler implements Http2FrameListener, Http2Connection.Listener {

    private final Map<Integer, PushNotificationPromise> unattachedResponsePromisesByStreamId = new IntObjectHashMap<>();

    private final Http2Connection.PropertyKey responseHeadersPropertyKey;
    private final Http2Connection.PropertyKey responsePromisePropertyKey;
    private final Http2Connection.PropertyKey streamErrorCausePropertyKey;

    private final String authority;

    private final long pingTimeoutMillis;
    private ScheduledFuture<?> pingTimeoutFuture;

    private Throwable connectionErrorCause;

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_EXPIRATION_HEADER = new AsciiString("apns-expiration");
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_PRIORITY_HEADER = new AsciiString("apns-priority");
    private static final AsciiString APNS_COLLAPSE_ID_HEADER = new AsciiString("apns-collapse-id");
    private static final AsciiString APNS_ID_HEADER = new AsciiString("apns-id");
    private static final AsciiString APNS_PUSH_TYPE_HEADER = new AsciiString("apns-push-type");

    private static final IOException STREAMS_EXHAUSTED_EXCEPTION =
            new IOException("HTTP/2 streams exhausted; closing connection.");

    private static final IOException STREAM_CLOSED_BEFORE_REPLY_EXCEPTION =
            new IOException("Stream closed before a reply was received");

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateAsTimeSinceEpochTypeAdapter(TimeUnit.MILLISECONDS))
            .create();

    private static final Logger log = LoggerFactory.getLogger(ApnsClientHandler.class);

    public static class ApnsClientHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<ApnsClientHandler, ApnsClientHandlerBuilder> {

        private String authority;
        private long idlePingIntervalMillis;

        ApnsClientHandlerBuilder authority(final String authority) {
            this.authority = authority;
            return this;
        }

        String authority() {
            return this.authority;
        }

        long idlePingIntervalMillis() {
            return idlePingIntervalMillis;
        }

        ApnsClientHandlerBuilder idlePingIntervalMillis(final long idlePingIntervalMillis) {
            this.idlePingIntervalMillis = idlePingIntervalMillis;
            return this;
        }

        @Override
        public ApnsClientHandlerBuilder frameLogger(final Http2FrameLogger frameLogger) {
            return super.frameLogger(frameLogger);
        }

        @Override
        public Http2FrameLogger frameLogger() {
            return super.frameLogger();
        }

        @Override
        protected final boolean isServer() {
            return false;
        }

        @Override
        protected boolean encoderEnforceMaxConcurrentStreams() {
            return true;
        }

        @Override
        public ApnsClientHandler build(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
            Objects.requireNonNull(this.authority(), "Authority must be set before building an ApnsClientHandler.");

            final ApnsClientHandler handler = new ApnsClientHandler(decoder, encoder, initialSettings, this.authority(), this.idlePingIntervalMillis());
            this.frameListener(handler);
            return handler;
        }

        @Override
        public ApnsClientHandler build() {
            return super.build();
        }
    }

    ApnsClientHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final String authority, final long idlePingIntervalMillis) {
        super(decoder, encoder, initialSettings);

        this.authority = authority;

        this.responseHeadersPropertyKey = this.connection().newKey();
        this.responsePromisePropertyKey = this.connection().newKey();
        this.streamErrorCausePropertyKey = this.connection().newKey();

        this.connection().addListener(this);

        this.pingTimeoutMillis = idlePingIntervalMillis / 2;
    }

    @Override
    public void write(final ChannelHandlerContext context, final Object message, final ChannelPromise writePromise) {
        if (message instanceof PushNotificationPromise) {
            final PushNotificationPromise pushNotificationPromise = (PushNotificationPromise) message;

            writePromise.addListener(new GenericFutureListener<ChannelPromise>() {

                @Override
                public void operationComplete(final ChannelPromise future) {
                    if (!future.isSuccess()) {
                        log.trace("Failed to write push notification.", future.cause());
                        pushNotificationPromise.tryFailure(future.cause());
                    }
                }
            });

            this.writePushNotification(context, pushNotificationPromise, writePromise);
        } else {
            // This should never happen, but in case some foreign debris winds up in the pipeline, just pass it through.
            log.error("Unexpected object in pipeline: {}", message);
            context.write(message, writePromise);
        }
    }

    void retryPushNotificationFromStream(final ChannelHandlerContext context, final int streamId) {
        final Http2Stream stream = this.connection().stream(streamId);

        final PushNotificationPromise responsePromise = stream.removeProperty(this.responsePromisePropertyKey);

        final ChannelPromise writePromise = context.channel().newPromise();
        this.writePushNotification(context, responsePromise, writePromise);
    }

    private void writePushNotification(final ChannelHandlerContext context, final PushNotificationPromise responsePromise, final ChannelPromise writePromise) {
        if (context.channel().isActive()) {
            final int streamId = this.connection().local().incrementAndGetNextStreamId();

            if (streamId > 0) {
                // We'll attach the push notification and response promise to the stream as soon as the stream is created.
                // Because we're using a StreamBufferingEncoder under the hood, there's no guarantee as to when the stream
                // will actually be created, and so we attach these in the onStreamAdded listener to make sure everything
                // is happening in a predictable order.
                this.unattachedResponsePromisesByStreamId.put(streamId, responsePromise);
                final ApnsPushNotification pushNotification = responsePromise.getPushNotification();

                final Http2Headers headers = getHeadersForPushNotification(pushNotification, context, streamId);

                final ChannelPromise headersPromise = context.newPromise();
                this.encoder().writeHeaders(context, streamId, headers, 0, false, headersPromise);
                log.trace("Wrote headers on stream {}: {}", streamId, headers);

                final ByteBuf payloadBuffer =
                        Unpooled.wrappedBuffer(pushNotification.getPayload().getBytes(StandardCharsets.UTF_8));

                final ChannelPromise dataPromise = context.newPromise();
                this.encoder().writeData(context, streamId, payloadBuffer, 0, true, dataPromise);
                log.trace("Wrote payload on stream {}: {}", streamId, pushNotification.getPayload());

                final PromiseCombiner promiseCombiner = new PromiseCombiner();
                promiseCombiner.addAll((ChannelFuture) headersPromise, dataPromise);
                promiseCombiner.finish(writePromise);
            } else {
                // This is very unlikely, but in the event that we run out of stream IDs, we need to open a new
                // connection. Just closing the context should be enough; automatic reconnection should take things
                // from there.
                writePromise.tryFailure(STREAMS_EXHAUSTED_EXCEPTION);
                context.channel().close();
            }
        } else {
            writePromise.tryFailure(STREAM_CLOSED_BEFORE_REPLY_EXCEPTION);
        }
    }

    protected Http2Headers getHeadersForPushNotification(final ApnsPushNotification pushNotification, final ChannelHandlerContext context, final int streamId) {
        final Http2Headers headers = new DefaultHttp2Headers()
                .method(HttpMethod.POST.asciiName())
                .authority(this.authority)
                .path(APNS_PATH_PREFIX + pushNotification.getToken())
                .scheme(HttpScheme.HTTPS.name())
                .addInt(APNS_EXPIRATION_HEADER, pushNotification.getExpiration() == null ? 0 : (int) (pushNotification.getExpiration().getTime() / 1000));

        if (pushNotification.getCollapseId() != null) {
            headers.add(APNS_COLLAPSE_ID_HEADER, pushNotification.getCollapseId());
        }

        if (pushNotification.getPriority() != null) {
            headers.addInt(APNS_PRIORITY_HEADER, pushNotification.getPriority().getCode());
        }

        if (pushNotification.getPushType() != null) {
            headers.add(APNS_PUSH_TYPE_HEADER, pushNotification.getPushType().getHeaderValue());
        }

        if (pushNotification.getTopic() != null) {
            headers.add(APNS_TOPIC_HEADER, pushNotification.getTopic());
        }

        if (pushNotification.getApnsId() != null) {
            headers.add(APNS_ID_HEADER, FastUUID.toString(pushNotification.getApnsId()));
        }

        return headers;
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext context, final Object event) throws Exception {
        if (event instanceof IdleStateEvent) {
            log.trace("Sending ping due to inactivity.");

            this.encoder().writePing(context, false, System.currentTimeMillis(), context.newPromise()).addListener(new GenericFutureListener<ChannelFuture>() {

                @Override
                public void operationComplete(final ChannelFuture future) {
                    if (!future.isSuccess()) {
                        log.debug("Failed to write PING frame.", future.cause());
                        future.channel().close();
                    }
                }
            });

            this.pingTimeoutFuture = context.channel().eventLoop().schedule(new Runnable() {

                @Override
                public void run() {
                    log.debug("Closing channel due to ping timeout.");
                    context.channel().close();
                }
            }, pingTimeoutMillis, TimeUnit.MILLISECONDS);

            this.flush(context);
        }

        super.userEventTriggered(context, event);
    }

    @Override
    public int onDataRead(final ChannelHandlerContext context, final int streamId, final ByteBuf data, final int padding, final boolean endOfStream) {
        final int bytesProcessed = data.readableBytes() + padding;

        if (endOfStream) {
            final Http2Stream stream = this.connection().stream(streamId);
            this.handleEndOfStream(context, stream, (Http2Headers) stream.getProperty(this.responseHeadersPropertyKey), data);
        } else {
            log.error("Gateway sent a DATA frame that was not the end of a stream.");
        }

        return bytesProcessed;
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int streamDependency, final short weight, final boolean exclusive, final int padding, final boolean endOfStream) {
        this.onHeadersRead(context, streamId, headers, padding, endOfStream);
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) {
        final Http2Stream stream = this.connection().stream(streamId);

        if (endOfStream) {
            this.handleEndOfStream(context, stream, headers, null);
        } else {
            stream.setProperty(this.responseHeadersPropertyKey, headers);
        }
    }

    private void handleEndOfStream(final ChannelHandlerContext context, final Http2Stream stream, final Http2Headers headers, final ByteBuf data) {

        final PushNotificationPromise<ApnsPushNotification, PushNotificationResponse<ApnsPushNotification>> responsePromise =
                stream.getProperty(this.responsePromisePropertyKey);

        final ApnsPushNotification pushNotification = responsePromise.getPushNotification();

        final HttpResponseStatus status = HttpResponseStatus.parseLine(headers.status());

        if (HttpResponseStatus.OK.equals(status)) {
            responsePromise.trySuccess(new SimplePushNotificationResponse<>(responsePromise.getPushNotification(),
                    true, getApnsIdFromHeaders(headers), null, null));
        } else {
            if (data != null) {
                final ErrorResponse errorResponse = GSON.fromJson(data.toString(StandardCharsets.UTF_8), ErrorResponse.class);
                this.handleErrorResponse(context, stream.id(), headers, pushNotification, errorResponse);
            } else {
                log.warn("Gateway sent an end-of-stream HEADERS frame for an unsuccessful notification.");
            }
        }
    }

    protected void handleErrorResponse(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final ApnsPushNotification pushNotification, final ErrorResponse errorResponse) {
        final PushNotificationPromise<ApnsPushNotification, PushNotificationResponse<ApnsPushNotification>> responsePromise =
                this.connection().stream(streamId).getProperty(this.responsePromisePropertyKey);

        final HttpResponseStatus status = HttpResponseStatus.parseLine(headers.status());

        responsePromise.trySuccess(new SimplePushNotificationResponse<>(responsePromise.getPushNotification(),
                HttpResponseStatus.OK.equals(status), getApnsIdFromHeaders(headers), errorResponse.getReason(),
                errorResponse.getTimestamp()));
    }

    private static UUID getApnsIdFromHeaders(final Http2Headers headers) {
        final CharSequence apnsIdSequence = headers.get(APNS_ID_HEADER);

        try {
            return apnsIdSequence != null ? FastUUID.parseUUID(apnsIdSequence) : null;
        } catch (final IllegalArgumentException e) {
            log.error("Failed to parse `apns-id` header: {}", apnsIdSequence, e);
            return null;
        }
    }

    @Override
    public void onPriorityRead(final ChannelHandlerContext ctx, final int streamId, final int streamDependency, final short weight, final boolean exclusive) {
    }

    @Override
    public void onRstStreamRead(final ChannelHandlerContext context, final int streamId, final long errorCode) {
        if (errorCode == Http2Error.REFUSED_STREAM.code()) {
            // This can happen if the server reduces MAX_CONCURRENT_STREAMS while we already have notifications in
            // flight. We may get multiple RST_STREAM frames per stream since we send multiple frames (HEADERS and
            // DATA) for each push notification, but we should only get one REFUSED_STREAM error; the rest should all be
            // STREAM_CLOSED.
            this.retryPushNotificationFromStream(context, streamId);
        }
    }

    @Override
    public void onSettingsAckRead(final ChannelHandlerContext ctx) {
    }

    @Override
    public void onSettingsRead(final ChannelHandlerContext context, final Http2Settings settings) {
        log.debug("Received settings from APNs gateway: {}", settings);

        // Always try to mark the "channel ready" promise as a success after we receive a SETTINGS frame. If it's the
        // first SETTINGS frame, we know all handshaking and connection setup is done and the channel is ready to use.
        // If it's a subsequent SETTINGS frame, this will have no effect.
        getChannelReadyPromise(context.channel()).trySuccess(context.channel());
    }

    @Override
    public void onPingRead(final ChannelHandlerContext ctx, final long pingData) {
    }

    @Override
    public void onPingAckRead(final ChannelHandlerContext context, final long pingData) {
        if (this.pingTimeoutFuture != null) {
            this.pingTimeoutFuture.cancel(false);
        } else {
            log.error("Received PING ACK, but no corresponding outbound PING found.");
        }
    }

    @Override
    public void onPushPromiseRead(final ChannelHandlerContext ctx, final int streamId, final int promisedStreamId, final Http2Headers headers, final int padding) {
    }

    @Override
    public void onGoAwayRead(final ChannelHandlerContext context, final int lastStreamId, final long errorCode, final ByteBuf debugData) {
        log.info("Received GOAWAY from APNs server: {}", debugData.toString(StandardCharsets.UTF_8));
    }

    @Override
    public void onWindowUpdateRead(final ChannelHandlerContext ctx, final int streamId, final int windowSizeIncrement) {
    }

    @Override
    public void onUnknownFrame(final ChannelHandlerContext ctx, final byte frameType, final int streamId, final Http2Flags flags, final ByteBuf payload) {
    }

    @Override
    public void onStreamAdded(final Http2Stream stream) {
        stream.setProperty(ApnsClientHandler.this.responsePromisePropertyKey, this.unattachedResponsePromisesByStreamId.remove(stream.id()));
    }

    @Override
    public void onStreamActive(final Http2Stream stream) {
    }

    @Override
    public void onStreamHalfClosed(final Http2Stream stream) {
    }

    @Override
    public void onStreamClosed(final Http2Stream stream) {
        // Always try to fail promises associated with closed streams; most of the time, this should fail silently, but
        // in cases of unexpected closure, it will make sure that nothing gets left hanging.
        final Promise<PushNotificationResponse<ApnsPushNotification>> responsePromise =
                stream.getProperty(this.responsePromisePropertyKey);

        if (responsePromise != null) {
            final Throwable cause;

            if (stream.getProperty(this.streamErrorCausePropertyKey) != null) {
                cause = stream.getProperty(this.streamErrorCausePropertyKey);
            } else if (this.connectionErrorCause != null) {
                cause = this.connectionErrorCause;
            } else {
                cause = STREAM_CLOSED_BEFORE_REPLY_EXCEPTION;
            }

            responsePromise.tryFailure(cause);
        }
    }

    @Override
    public void onStreamRemoved(final Http2Stream stream) {
        stream.removeProperty(this.responseHeadersPropertyKey);
        stream.removeProperty(this.responsePromisePropertyKey);
    }

    @Override
    public void onGoAwaySent(final int lastStreamId, final long errorCode, final ByteBuf debugData) {
    }

    @Override
    public void onGoAwayReceived(final int lastStreamId, final long errorCode, final ByteBuf debugData) {
    }

    @Override
    protected void onStreamError(final ChannelHandlerContext context, final boolean isOutbound, final Throwable cause, final Http2Exception.StreamException streamException) {
        final Http2Stream stream = this.connection().stream(streamException.streamId());
        stream.setProperty(this.streamErrorCausePropertyKey, streamException);

        super.onStreamError(context, isOutbound, cause, streamException);
    }

    @Override
    protected void onConnectionError(final ChannelHandlerContext context, final boolean isOutbound, final Throwable cause, final Http2Exception http2Exception) {
        this.connectionErrorCause = http2Exception != null ? http2Exception : cause;

        super.onConnectionError(context, isOutbound, cause, http2Exception);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext context) throws Exception {
        super.channelInactive(context);

        for (final PushNotificationPromise promise : this.unattachedResponsePromisesByStreamId.values()) {
            promise.tryFailure(STREAM_CLOSED_BEFORE_REPLY_EXCEPTION);
        }

        this.unattachedResponsePromisesByStreamId.clear();
    }

    public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
        // Always try to fail the "channel ready" promise if we catch an exception; in some cases, these may happen
        // after a connection has already become ready, in which case the failure attempt will have no effect.
        getChannelReadyPromise(context.channel()).tryFailure(cause);
    }

    private Promise<Channel> getChannelReadyPromise(final Channel channel) {
        return channel.attr(ApnsChannelFactory.CHANNEL_READY_PROMISE_ATTRIBUTE_KEY).get();
    }
}
