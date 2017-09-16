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

package com.turo.pushy.apns.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.turo.pushy.apns.util.DateAsTimeSinceEpochTypeAdapter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.*;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.PromiseCombiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

class MockApnsServerHandler extends Http2ConnectionHandler implements Http2FrameListener {

    private final PushNotificationHandler pushNotificationHandler;

    private final Http2Connection.PropertyKey headersPropertyKey;
    private final Http2Connection.PropertyKey payloadPropertyKey;

    private static final AsciiString APNS_ID_HEADER = new AsciiString("apns-id");

    private static final Http2Headers SUCCESS_HEADERS = new DefaultHttp2Headers().status(HttpResponseStatus.OK.codeAsText());

    private static final int MAX_CONTENT_LENGTH = 4096;

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateAsTimeSinceEpochTypeAdapter(TimeUnit.MILLISECONDS))
            .create();

    private static final Logger log = LoggerFactory.getLogger(MockApnsServerHandler.class);

    public static class MockApnsServerHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<MockApnsServerHandler, MockApnsServerHandler.MockApnsServerHandlerBuilder> {

        private PushNotificationHandler pushNotificationHandler;

        MockApnsServerHandlerBuilder pushNotificationHandler(final PushNotificationHandler pushNotificationHandler) {
            this.pushNotificationHandler = pushNotificationHandler;
            return this;
        }

        @Override
        public MockApnsServerHandlerBuilder initialSettings(final Http2Settings initialSettings) {
            return super.initialSettings(initialSettings);
        }

        @Override
        public MockApnsServerHandler build(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
            final MockApnsServerHandler handler = new MockApnsServerHandler(decoder, encoder, initialSettings, this.pushNotificationHandler);
            this.frameListener(handler);
            return handler;
        }

        @Override
        public MockApnsServerHandler build() {
            return super.build();
        }
    }

    private static class AcceptNotificationResponse {
        private final int streamId;

        AcceptNotificationResponse(final int streamId) {
            this.streamId = streamId;
        }

        int getStreamId() {
            return this.streamId;
        }
    }

    private static class RejectNotificationResponse {
        private final int streamId;
        private final UUID apnsId;
        private final RejectionReason errorReason;
        private final Date timestamp;

        RejectNotificationResponse(final int streamId, final UUID apnsId, final RejectionReason errorReason, final Date timestamp) {
            this.streamId = streamId;
            this.apnsId = apnsId;
            this.errorReason = errorReason;
            this.timestamp = timestamp;
        }

        int getStreamId() {
            return this.streamId;
        }

        UUID getApnsId() {
            return this.apnsId;
        }

        RejectionReason getErrorReason() {
            return this.errorReason;
        }

        Date getTimestamp() {
            return this.timestamp;
        }
    }

    private static class InternalServerErrorResponse {
        private final int streamId;

        InternalServerErrorResponse(final int streamId) {
            this.streamId = streamId;
        }

        int getStreamId() {
            return this.streamId;
        }
    }

    protected static class ErrorPayload {
        private final String reason;
        private final Date timestamp;

        ErrorPayload(final String reason, final Date timestamp) {
            this.reason = reason;
            this.timestamp = timestamp;
        }

        String getReason() {
            return this.reason;
        }

        Date getTimestamp() {
            return this.timestamp;
        }
    }

    MockApnsServerHandler(final Http2ConnectionDecoder decoder,
                          final Http2ConnectionEncoder encoder,
                          final Http2Settings initialSettings,
                          final PushNotificationHandler pushNotificationHandler) {

        super(decoder, encoder, initialSettings);

        this.headersPropertyKey = this.connection().newKey();
        this.payloadPropertyKey = this.connection().newKey();

        this.pushNotificationHandler = pushNotificationHandler;
    }

    @Override
    public int onDataRead(final ChannelHandlerContext context, final int streamId, final ByteBuf data, final int padding, final boolean endOfStream) throws Http2Exception {
        final int bytesProcessed = data.readableBytes() + padding;

        final Http2Stream stream = this.connection().stream(streamId);

        if (stream.getProperty(this.payloadPropertyKey) == null) {
            stream.setProperty(this.payloadPropertyKey, data.alloc().heapBuffer(MAX_CONTENT_LENGTH));
        }

        ((ByteBuf) stream.getProperty(this.payloadPropertyKey)).writeBytes(data);

        if (endOfStream) {
            this.handleEndOfStream(context, stream);
        }

        return bytesProcessed;
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) throws Http2Exception {
        final Http2Stream stream = this.connection().stream(streamId);
        stream.setProperty(this.headersPropertyKey, headers);

        if (endOfStream) {
            this.handleEndOfStream(context, stream);
        }
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers, final int streamDependency,
                              final short weight, final boolean exclusive, final int padding, final boolean endOfStream) throws Http2Exception {

        this.onHeadersRead(ctx, streamId, headers, padding, endOfStream);
    }

    @Override
    public void onPriorityRead(final ChannelHandlerContext ctx, final int streamId, final int streamDependency, final short weight, final boolean exclusive) throws Http2Exception {
    }

    @Override
    public void onRstStreamRead(final ChannelHandlerContext ctx, final int streamId, final long errorCode) throws Http2Exception {
    }

    @Override
    public void onSettingsAckRead(final ChannelHandlerContext ctx) throws Http2Exception {
    }

    @Override
    public void onSettingsRead(final ChannelHandlerContext ctx, final Http2Settings settings) throws Http2Exception {
    }

    @Override
    public void onPingRead(final ChannelHandlerContext ctx, final ByteBuf data) throws Http2Exception {
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

    private void handleEndOfStream(final ChannelHandlerContext context, final Http2Stream stream) {
        final Http2Headers headers = stream.getProperty(this.headersPropertyKey);
        final ByteBuf payload = stream.getProperty(this.payloadPropertyKey);
        final ChannelPromise writePromise = context.newPromise();

        try {
            this.pushNotificationHandler.handlePushNotification(headers, payload);
            this.write(context, new AcceptNotificationResponse(stream.id()), writePromise);
        } catch (final RejectedNotificationException e) {
            final Date deviceTokenExpirationTimestamp = e instanceof UnregisteredDeviceTokenException ?
                    ((UnregisteredDeviceTokenException) e).getDeviceTokenExpirationTimestamp() : null;

            this.write(context, new RejectNotificationResponse(stream.id(), e.getApnsId(), e.getRejectionReason(), deviceTokenExpirationTimestamp), writePromise);
        } catch (final Exception e) {
            this.write(context, new InternalServerErrorResponse(stream.id()), writePromise);
        } finally {
            if (stream.getProperty(this.payloadPropertyKey) != null) {
                ((ByteBuf) stream.getProperty(this.payloadPropertyKey)).release();
            }

            this.flush(context);
        }
    }

    @Override
    public void write(final ChannelHandlerContext context, final Object message, final ChannelPromise writePromise) {
        if (message instanceof AcceptNotificationResponse) {
            final AcceptNotificationResponse acceptNotificationResponse = (AcceptNotificationResponse) message;
            this.encoder().writeHeaders(context, acceptNotificationResponse.getStreamId(), SUCCESS_HEADERS, 0, true, writePromise);

            log.trace("Accepted push notification on stream {}", acceptNotificationResponse.getStreamId());
        } else if (message instanceof RejectNotificationResponse) {
            final RejectNotificationResponse rejectNotificationResponse = (RejectNotificationResponse) message;

            final Http2Headers headers = new DefaultHttp2Headers();
            headers.status(rejectNotificationResponse.getErrorReason().getHttpResponseStatus().codeAsText());
            headers.add(HttpHeaderNames.CONTENT_TYPE, "application/json");

            if (rejectNotificationResponse.getApnsId() != null) {
                headers.add(APNS_ID_HEADER, rejectNotificationResponse.getApnsId().toString());
            }

            final byte[] payloadBytes;
            {
                final ErrorPayload errorPayload =
                        new ErrorPayload(rejectNotificationResponse.getErrorReason().getReasonText(),
                                rejectNotificationResponse.getTimestamp());

                payloadBytes = GSON.toJson(errorPayload).getBytes();
            }

            final ChannelPromise headersPromise = context.newPromise();
            this.encoder().writeHeaders(context, rejectNotificationResponse.getStreamId(), headers, 0, false, headersPromise);

            final ChannelPromise dataPromise = context.newPromise();
            this.encoder().writeData(context, rejectNotificationResponse.getStreamId(), Unpooled.wrappedBuffer(payloadBytes), 0, true, dataPromise);

            final PromiseCombiner promiseCombiner = new PromiseCombiner();
            promiseCombiner.addAll((ChannelFuture) headersPromise, dataPromise);
            promiseCombiner.finish(writePromise);

            log.trace("Rejected push notification on stream {}: {}", rejectNotificationResponse.getStreamId(), rejectNotificationResponse.getErrorReason());
        } else if (message instanceof InternalServerErrorResponse) {
            final InternalServerErrorResponse internalServerErrorResponse = (InternalServerErrorResponse) message;

            final Http2Headers headers = new DefaultHttp2Headers();
            headers.status(HttpResponseStatus.INTERNAL_SERVER_ERROR.codeAsText());

            this.encoder().writeHeaders(context, internalServerErrorResponse.getStreamId(), headers, 0, true, writePromise);

            log.trace("Encountered an internal error on stream {}", internalServerErrorResponse.getStreamId());
        } else {
            context.write(message, writePromise);
        }
    }
}
