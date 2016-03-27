package com.relayrides.pushy.apns;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
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
import io.netty.util.concurrent.PromiseCombiner;

class MockApnsServerHandler extends Http2ConnectionHandler implements Http2FrameListener {

    private final MockApnsServer apnsServer;
    private final Set<String> topics;

    private final Map<Integer, UUID> requestsWaitingForDataFrame = new HashMap<>();

    private static final Http2Headers SUCCESS_HEADERS = new DefaultHttp2Headers()
            .status(HttpResponseStatus.OK.codeAsText());

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_PRIORITY_HEADER = new AsciiString("apns-priority");
    private static final AsciiString APNS_ID_HEADER = new AsciiString("apns-id");

    private static final int MAX_CONTENT_LENGTH = 4096;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateAsMillisecondsSinceEpochTypeAdapter())
            .create();

    public static final class MockApnsServerHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<MockApnsServerHandler, MockApnsServerHandlerBuilder> {
        private MockApnsServer apnsServer;
        private Set<String> topics;

        public MockApnsServerHandlerBuilder apnsServer(final MockApnsServer apnsServer) {
            this.apnsServer = apnsServer;
            return this;
        }

        public MockApnsServer apnsServer() {
            return this.apnsServer;
        }

        public MockApnsServerHandlerBuilder topics(final Set<String> topics) {
            this.topics = topics;
            return this;
        }

        public Set<String> topics() {
            return this.topics;
        }

        @Override
        public MockApnsServerHandlerBuilder initialSettings(final Http2Settings initialSettings) {
            return super.initialSettings(initialSettings);
        }

        @Override
        public MockApnsServerHandler build(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
            final MockApnsServerHandler handler = new MockApnsServerHandler(decoder, encoder, initialSettings, this.apnsServer(), this.topics());
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

        public AcceptNotificationResponse(final int streamId) {
            this.streamId = streamId;
        }

        public int getStreamId() {
            return this.streamId;
        }
    }

    private static class RejectNotificationResponse {
        private final int streamId;
        private final UUID apnsId;
        private final ErrorReason errorReason;
        private final Date timestamp;

        public RejectNotificationResponse(final int streamId, final UUID apnsId, final ErrorReason errorReason) {
            this(streamId, apnsId, errorReason, null);
        }

        public RejectNotificationResponse(final int streamId, final UUID apnsId, final ErrorReason errorReason, final Date timestamp) {
            this.streamId = streamId;
            this.apnsId = apnsId;
            this.errorReason = errorReason;
            this.timestamp = timestamp;
        }

        public int getStreamId() {
            return this.streamId;
        }

        public UUID getApnsId() {
            return this.apnsId;
        }

        public ErrorReason getErrorReason() {
            return this.errorReason;
        }

        public Date getTimestamp() {
            return this.timestamp;
        }
    }

    protected MockApnsServerHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final MockApnsServer apnsServer, final Set<String> topics) {
        super(decoder, encoder, initialSettings);
        this.topics = topics;
        this.apnsServer = apnsServer;
    }

    @Override
    public int onDataRead(final ChannelHandlerContext context, final int streamId, final ByteBuf data, final int padding, final boolean endOfStream) throws Http2Exception {
        final int bytesProcessed = data.readableBytes() + padding;

        if (endOfStream) {
            // Presumably, we spotted an error earlier and sent a response immediately if we don't have an entry in the
            // "waiting for data frame" map.
            if (this.requestsWaitingForDataFrame.containsKey(streamId)) {
                final UUID apnsId = this.requestsWaitingForDataFrame.remove(streamId);

                if (data.readableBytes() <= MAX_CONTENT_LENGTH) {
                    context.channel().writeAndFlush(new AcceptNotificationResponse(streamId));
                } else {
                    context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.PAYLOAD_TOO_LARGE));
                }
            }
        }

        return bytesProcessed;
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) throws Http2Exception {
        if (!HttpMethod.POST.asciiName().contentEquals(headers.get(Http2Headers.PseudoHeaderName.METHOD.value()))) {
            context.channel().writeAndFlush(new RejectNotificationResponse(streamId, null, ErrorReason.METHOD_NOT_ALLOWED));
            return;
        }

        final UUID apnsId;
        {
            final CharSequence apnsIdSequence = headers.get(APNS_ID_HEADER);

            if (apnsIdSequence != null) {
                try {
                    apnsId = UUID.fromString(apnsIdSequence.toString());
                } catch (final IllegalArgumentException e) {
                    context.channel().writeAndFlush(new RejectNotificationResponse(streamId, null, ErrorReason.BAD_MESSAGE_ID));
                    return;
                }
            } else {
                // If the client didn't send us a UUID, make one up (for now)
                apnsId = UUID.randomUUID();
            }
        }

        if (endOfStream) {
            context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.PAYLOAD_EMPTY));
            return;
        }

        {
            final Integer contentLength = headers.getInt(HttpHeaderNames.CONTENT_LENGTH);

            if (contentLength != null && contentLength > MAX_CONTENT_LENGTH) {
                context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.PAYLOAD_TOO_LARGE));
                return;
            } else if (contentLength == null) {
                context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.PAYLOAD_EMPTY));
                return;
            }
        }

        final String topic;
        {
            final CharSequence topicSequence = headers.get(APNS_TOPIC_HEADER);

            if (topicSequence != null) {
                final String topicString = topicSequence.toString();

                if (this.topics.contains(topicString)) {
                    topic = topicSequence.toString();
                } else {
                    context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.TOPIC_DIALLOWED));
                    return;
                }
            } else {
                if (this.topics.size() == 1) {
                    topic = this.topics.iterator().next();
                } else {
                    context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.MISSING_TOPIC));
                    return;
                }
            }
        }

        {
            final Integer priorityCode = headers.getInt(APNS_PRIORITY_HEADER);

            if (priorityCode != null) {
                try {
                    DeliveryPriority.getFromCode(priorityCode);
                } catch (final IllegalArgumentException e) {
                    context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.BAD_PRIORITY));
                    return;
                }
            }
        }

        {
            final CharSequence pathSequence = headers.get(Http2Headers.PseudoHeaderName.PATH.value());

            if (pathSequence != null) {
                final String pathString = pathSequence.toString();

                if (pathString.startsWith(APNS_PATH_PREFIX)) {
                    final String tokenString = pathString.substring(APNS_PATH_PREFIX.length());

                    final Matcher tokenMatcher = TOKEN_PATTERN.matcher(tokenString);

                    if (!tokenMatcher.matches()) {
                        context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.BAD_DEVICE_TOKEN));
                        return;
                    }

                    final Date expirationTimestamp = this.apnsServer.getExpirationTimestampForTokenInTopic(tokenString, topic);

                    if (expirationTimestamp != null) {
                        context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.UNREGISTERED, expirationTimestamp));
                        return;
                    }

                    if (!this.apnsServer.isTokenRegisteredForTopic(tokenString, topic)) {
                        context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.DEVICE_TOKEN_NOT_FOR_TOPIC));
                        return;
                    }
                }
            } else {
                context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.BAD_PATH));
                return;
            }
        }

        this.requestsWaitingForDataFrame.put(streamId, apnsId);
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

    @Override
    public void write(final ChannelHandlerContext context, final Object message, final ChannelPromise writePromise) throws Exception {
        if (message instanceof AcceptNotificationResponse) {
            final AcceptNotificationResponse acceptNotificationResponse = (AcceptNotificationResponse) message;
            this.encoder().writeHeaders(context, acceptNotificationResponse.getStreamId(), SUCCESS_HEADERS, 0, true, writePromise);
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
                final ErrorResponse errorResponse =
                        new ErrorResponse(rejectNotificationResponse.getErrorReason().getReasonText(),
                                rejectNotificationResponse.getTimestamp());

                payloadBytes = gson.toJson(errorResponse).getBytes();
            }

            final ChannelPromise headersPromise = context.newPromise();
            this.encoder().writeHeaders(context, rejectNotificationResponse.getStreamId(), headers, 0, false, headersPromise);

            final ChannelPromise dataPromise = context.newPromise();
            this.encoder().writeData(context, rejectNotificationResponse.getStreamId(), Unpooled.wrappedBuffer(payloadBytes), 0, true, dataPromise);

            final PromiseCombiner promiseCombiner = new PromiseCombiner();
            promiseCombiner.addAll(headersPromise, dataPromise);
            promiseCombiner.finish(writePromise);
        } else {
            context.write(message, writePromise);
        }
    }
}
