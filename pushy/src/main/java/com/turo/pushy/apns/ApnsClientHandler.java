/*
 * Copyright (c) 2013-2017 Turo
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

package com.turo.pushy.apns;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

class ApnsClientHandler extends Http2ConnectionHandler implements Http2FrameListener, Http2Connection.Listener {

    private final Http2Connection.PropertyKey pushNotificationPropertyKey;
    private final Http2Connection.PropertyKey responseHeadersPropertyKey;
    private final Http2Connection.PropertyKey responsePromisePropertyKey;

    private final String authority;

    private final long pingTimeoutMillis;
    private ScheduledFuture<?> pingTimeoutFuture;

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_EXPIRATION_HEADER = new AsciiString("apns-expiration");
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_PRIORITY_HEADER = new AsciiString("apns-priority");
    private static final AsciiString APNS_COLLAPSE_ID_HEADER = new AsciiString("apns-collapse-id");

    private static final int INITIAL_PAYLOAD_BUFFER_CAPACITY = 4096;

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

        public ApnsClientHandlerBuilder authority(final String authority) {
            this.authority = authority;
            return this;
        }

        public String authority() {
            return this.authority;
        }

        public long idlePingIntervalMillis() {
            return idlePingIntervalMillis;
        }

        public ApnsClientHandlerBuilder idlePingIntervalMillis(long idlePingIntervalMillis) {
            this.idlePingIntervalMillis = idlePingIntervalMillis;
            return this;
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

    protected ApnsClientHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final String authority, final long idlePingIntervalMillis) {
        super(decoder, encoder, initialSettings);

        this.authority = authority;

        this.pushNotificationPropertyKey = this.connection().newKey();
        this.responseHeadersPropertyKey = this.connection().newKey();
        this.responsePromisePropertyKey = this.connection().newKey();

        this.connection().addListener(this);

        this.pingTimeoutMillis = idlePingIntervalMillis / 2;
    }

    @Override
    public void write(final ChannelHandlerContext context, final Object message, final ChannelPromise writePromise) throws Http2Exception, InvalidKeyException, NoSuchAlgorithmException {
        if (message instanceof PushNotificationAndResponsePromise) {
            final PushNotificationAndResponsePromise pushNotificationAndResponsePromise =
                    (PushNotificationAndResponsePromise) message;

            this.writePushNotification(context, pushNotificationAndResponsePromise.getPushNotification(), pushNotificationAndResponsePromise.getResponsePromise(), writePromise);
        } else {
            // This should never happen, but in case some foreign debris winds up in the pipeline, just pass it through.
            log.error("Unexpected object in pipeline: {}", message);
            context.write(message, writePromise);
        }
    }

    protected void retryPushNotificationFromStream(final ChannelHandlerContext context, final int streamId) {
        final Http2Stream stream = this.connection().stream(streamId);

        final ApnsPushNotification pushNotification = stream.getProperty(this.pushNotificationPropertyKey);
        final Promise<PushNotificationResponse<ApnsPushNotification>> responsePromise = stream.removeProperty(this.responsePromisePropertyKey);

        final ChannelPromise writePromise = context.channel().newPromise();
        this.writePushNotification(context, pushNotification, responsePromise, writePromise);

        writePromise.addListener(new GenericFutureListener<Future<Void>>() {
            @Override
            public void operationComplete(final Future<Void> writeFuture) throws Exception {
                if (!writeFuture.isSuccess()) {
                    responsePromise.tryFailure(writeFuture.cause());
                }
            }
        });
    }

    private void writePushNotification(final ChannelHandlerContext context, final ApnsPushNotification pushNotification, final Promise<PushNotificationResponse<ApnsPushNotification>> responsePromise, final ChannelPromise writePromise) {
        final int streamId = this.connection().local().incrementAndGetNextStreamId();

        if (streamId > 0) {
            final Http2Headers headers = getHeadersForPushNotification(pushNotification, streamId);

            final ChannelPromise headersPromise = context.newPromise();
            this.encoder().writeHeaders(context, streamId, headers, 0, false, headersPromise);
            log.trace("Wrote headers on stream {}: {}", streamId, headers);

            final ByteBuf payloadBuffer = context.alloc().ioBuffer(INITIAL_PAYLOAD_BUFFER_CAPACITY);
            payloadBuffer.writeBytes(pushNotification.getPayload().getBytes(StandardCharsets.UTF_8));

            final ChannelPromise dataPromise = context.newPromise();
            this.encoder().writeData(context, streamId, payloadBuffer, 0, true, dataPromise);
            log.trace("Wrote payload on stream {}: {}", streamId, pushNotification.getPayload());

            final PromiseCombiner promiseCombiner = new PromiseCombiner();
            promiseCombiner.addAll((ChannelFuture) headersPromise, dataPromise);
            promiseCombiner.finish(writePromise);

            writePromise.addListener(new GenericFutureListener<ChannelPromise>() {

                @Override
                public void operationComplete(final ChannelPromise future) throws Exception {
                    if (future.isSuccess()) {
                        final Http2Stream stream = ApnsClientHandler.this.connection().stream(streamId);

                        stream.setProperty(ApnsClientHandler.this.pushNotificationPropertyKey, pushNotification);
                        stream.setProperty(ApnsClientHandler.this.responsePromisePropertyKey, responsePromise);
                    } else {
                        log.trace("Failed to write push notification on stream {}.", streamId, future.cause());
                        responsePromise.tryFailure(future.cause());
                    }
                }
            });
        } else {
            // This is very unlikely, but in the event that we run out of stream IDs, we need to open a new
            // connection. Just closing the context should be enough; automatic reconnection should take things
            // from there.
            writePromise.tryFailure(STREAMS_EXHAUSTED_EXCEPTION);
            context.channel().close();
        }
    }

    protected Http2Headers getHeadersForPushNotification(final ApnsPushNotification pushNotification, final int streamId) {
        final Http2Headers headers = new DefaultHttp2Headers()
                .method(HttpMethod.POST.asciiName())
                .authority(this.authority)
                .path(APNS_PATH_PREFIX + pushNotification.getToken())
                .addInt(APNS_EXPIRATION_HEADER, pushNotification.getExpiration() == null ? 0 : (int) (pushNotification.getExpiration().getTime() / 1000));

        if (pushNotification.getCollapseId() != null) {
            headers.add(APNS_COLLAPSE_ID_HEADER, pushNotification.getCollapseId());
        }

        if (pushNotification.getPriority() != null) {
            headers.addInt(APNS_PRIORITY_HEADER, pushNotification.getPriority().getCode());
        }

        if (pushNotification.getTopic() != null) {
            headers.add(APNS_TOPIC_HEADER, pushNotification.getTopic());
        }

        return headers;
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext context, final Object event) throws Exception {
        if (event instanceof IdleStateEvent) {
            log.trace("Sending ping due to inactivity.");

            final ByteBuf pingDataBuffer = context.alloc().ioBuffer(Long.SIZE, Long.SIZE);
            pingDataBuffer.writeLong(System.currentTimeMillis());

            this.encoder().writePing(context, false, pingDataBuffer, context.newPromise()).addListener(new GenericFutureListener<ChannelFuture>() {

                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
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
    public int onDataRead(final ChannelHandlerContext context, final int streamId, final ByteBuf data, final int padding, final boolean endOfStream) throws Http2Exception {
        log.trace("Received data from APNs gateway on stream {}: {}", streamId, data.toString(StandardCharsets.UTF_8));

        final int bytesProcessed = data.readableBytes() + padding;

        if (endOfStream) {
            final Http2Stream stream = this.connection().stream(streamId);

            final Http2Headers headers = stream.getProperty(this.responseHeadersPropertyKey);
            final ApnsPushNotification pushNotification = stream.getProperty(this.pushNotificationPropertyKey);

            final ErrorResponse errorResponse = GSON.fromJson(data.toString(StandardCharsets.UTF_8), ErrorResponse.class);

            this.handleErrorResponse(context, streamId, headers, pushNotification, errorResponse);
        } else {
            log.error("Gateway sent a DATA frame that was not the end of a stream.");
        }

        return bytesProcessed;
    }

    protected void handleErrorResponse(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final ApnsPushNotification pushNotification, final ErrorResponse errorResponse) {
        final Promise<PushNotificationResponse<ApnsPushNotification>> responsePromise =
                this.connection().stream(streamId).getProperty(this.responsePromisePropertyKey);

        final HttpResponseStatus status = HttpResponseStatus.parseLine(headers.status());

        if (HttpResponseStatus.INTERNAL_SERVER_ERROR.equals(status)) {
            log.warn("APNs server reported an internal error when sending {}.", pushNotification);
            responsePromise.tryFailure(new ApnsServerException(GSON.toJson(errorResponse)));
        } else {
            responsePromise.trySuccess(new SimplePushNotificationResponse<>(pushNotification,
                    HttpResponseStatus.OK.equals(status), errorResponse.getReason(), errorResponse.getTimestamp()));
        }
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int streamDependency, final short weight, final boolean exclusive, final int padding, final boolean endOfStream) throws Http2Exception {
        this.onHeadersRead(context, streamId, headers, padding, endOfStream);
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) throws Http2Exception {
        log.trace("Received headers from APNs gateway on stream {}: {}", streamId, headers);
        final Http2Stream stream = this.connection().stream(streamId);

        if (endOfStream) {
            final HttpResponseStatus status = HttpResponseStatus.parseLine(headers.status());
            final boolean success = HttpResponseStatus.OK.equals(status);

            if (!success) {
                log.warn("Gateway sent an end-of-stream HEADERS frame for an unsuccessful notification.");
            }

            final ApnsPushNotification pushNotification = stream.getProperty(this.pushNotificationPropertyKey);
            final Promise<PushNotificationResponse<ApnsPushNotification>> responsePromise = stream.getProperty(this.responsePromisePropertyKey);

            if (HttpResponseStatus.INTERNAL_SERVER_ERROR.equals(status)) {
                log.warn("APNs server reported an internal error when sending {}.", pushNotification);
                responsePromise.tryFailure(new ApnsServerException());
            } else {
                responsePromise.trySuccess(
                        new SimplePushNotificationResponse<>(pushNotification, success, null, null));
            }
        } else {
            stream.setProperty(this.responseHeadersPropertyKey, headers);
        }
    }

    @Override
    public void onPriorityRead(final ChannelHandlerContext ctx, final int streamId, final int streamDependency, final short weight, final boolean exclusive) throws Http2Exception {
    }

    @Override
    public void onRstStreamRead(final ChannelHandlerContext context, final int streamId, final long errorCode) throws Http2Exception {
        if (errorCode == Http2Error.REFUSED_STREAM.code()) {
            // This can happen if the server reduces MAX_CONCURRENT_STREAMS while we already have notifications in
            // flight. We may get multiple RST_STREAM frames per stream since we send multiple frames (HEADERS and
            // DATA) for each push notification, but we should only get one REFUSED_STREAM error; the rest should all be
            // STREAM_CLOSED.
            this.retryPushNotificationFromStream(context, streamId);
        }
    }

    @Override
    public void onSettingsAckRead(final ChannelHandlerContext ctx) throws Http2Exception {
    }

    @Override
    public void onSettingsRead(final ChannelHandlerContext context, final Http2Settings settings) {
        log.trace("Received settings from APNs gateway: {}", settings);
    }

    @Override
    public void onPingRead(final ChannelHandlerContext ctx, final ByteBuf data) throws Http2Exception {
    }

    @Override
    public void onPingAckRead(final ChannelHandlerContext context, final ByteBuf data) {
        if (this.pingTimeoutFuture != null) {
            log.trace("Received reply to ping.");
            this.pingTimeoutFuture.cancel(false);
        } else {
            log.error("Received PING ACK, but no corresponding outbound PING found.");
        }
    }

    @Override
    public void onPushPromiseRead(final ChannelHandlerContext ctx, final int streamId, final int promisedStreamId, final Http2Headers headers, final int padding) throws Http2Exception {
    }

    @Override
    public void onGoAwayRead(final ChannelHandlerContext context, final int lastStreamId, final long errorCode, final ByteBuf debugData) throws Http2Exception {
        log.info("Received GOAWAY from APNs server: {}", debugData.toString(StandardCharsets.UTF_8));
    }

    @Override
    public void onWindowUpdateRead(final ChannelHandlerContext ctx, final int streamId, final int windowSizeIncrement) throws Http2Exception {
    }

    @Override
    public void onUnknownFrame(final ChannelHandlerContext ctx, final byte frameType, final int streamId, final Http2Flags flags, final ByteBuf payload) throws Http2Exception {
    }

    @Override
    public void onStreamAdded(final Http2Stream stream) {
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
            responsePromise.tryFailure(STREAM_CLOSED_BEFORE_REPLY_EXCEPTION);
        }
    }

    @Override
    public void onStreamRemoved(final Http2Stream stream) {
        stream.removeProperty(this.pushNotificationPropertyKey);
        stream.removeProperty(this.responseHeadersPropertyKey);
        stream.removeProperty(this.responsePromisePropertyKey);
    }

    @Override
    public void onGoAwaySent(final int lastStreamId, final long errorCode, final ByteBuf debugData) {
    }

    @Override
    public void onGoAwayReceived(final int lastStreamId, final long errorCode, final ByteBuf debugData) {
    }
}
