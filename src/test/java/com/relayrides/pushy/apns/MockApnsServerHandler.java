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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
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

class MockApnsServerHandler extends Http2ConnectionHandler implements Http2FrameListener {

    private final MockApnsServer apnsServer;
    private final Set<String> topics;

    private final Map<Integer, UUID> requestsWaitingForDataFrame = new HashMap<Integer, UUID>();

    private static final Http2Headers SUCCESS_HEADERS = new DefaultHttp2Headers()
            .status(HttpResponseStatus.OK.codeAsText());

    private static final AsciiString APNS_ID = new AsciiString("apns-id");
    private static final AsciiString APNS_TOPIC = new AsciiString("apns-topic");

    private static final int MAX_CONTENT_LENGTH = 4096;

    private static final String PATH_PREFIX = "/3/device/";

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateAsSecondsSinceEpochTypeAdapter())
            .create();

    public static final class Builder extends BuilderBase<MockApnsServerHandler, Builder> {
        private MockApnsServer apnsServer;
        private Set<String> topics;

        public Builder apnsServer(final MockApnsServer apnsServer) {
            this.apnsServer = apnsServer;
            return this;
        }

        public MockApnsServer apnsServer() {
            return this.apnsServer;
        }

        public Builder topics(final Set<String> topics) {
            this.topics = topics;
            return this;
        }

        public Set<String> topics() {
            return this.topics;
        }

        @Override
        public MockApnsServerHandler build0(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder) {
            final MockApnsServerHandler handler = new MockApnsServerHandler(decoder, encoder, this.initialSettings(), this.apnsServer(), this.topics());
            this.frameListener(handler);
            return handler;
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
                    this.sendSuccessResponse(context, streamId);
                } else {
                    this.sendErrorResponse(context, streamId, apnsId, ErrorReason.PAYLOAD_TOO_LARGE);
                }
            }
        }

        return bytesProcessed;
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) throws Http2Exception {
        if (!HttpMethod.POST.asciiName().contentEquals(headers.get(Http2Headers.PseudoHeaderName.METHOD.value()))) {
            this.sendErrorResponse(context, streamId, null, ErrorReason.METHOD_NOT_ALLOWED);
            return;
        }

        final UUID apnsId;
        {
            final CharSequence apnsIdSequence = headers.get(APNS_ID);

            if (apnsIdSequence != null) {
                try {
                    apnsId = UUID.fromString(apnsIdSequence.toString());
                } catch (final IllegalArgumentException e) {
                    this.sendErrorResponse(context, streamId, null, ErrorReason.BAD_MESSAGE_ID);
                    return;
                }
            } else {
                // If the client didn't send us a UUID, make one up (for now)
                apnsId = UUID.randomUUID();
            }

            this.requestsWaitingForDataFrame.put(streamId, apnsId);
        }

        if (endOfStream) {
            this.sendErrorResponse(context, streamId, apnsId, ErrorReason.PAYLOAD_EMPTY);
            return;
        }

        {
            final Integer contentLength = headers.getInt(HttpHeaderNames.CONTENT_LENGTH);

            if (contentLength != null && contentLength > MAX_CONTENT_LENGTH) {
                this.sendErrorResponse(context, streamId, apnsId, ErrorReason.PAYLOAD_TOO_LARGE);
                return;
            } else if (contentLength == null) {
                this.sendErrorResponse(context, streamId, apnsId, ErrorReason.PAYLOAD_EMPTY);
                return;
            }
        }

        final String topic;
        {
            final CharSequence topicSequence = headers.get(APNS_TOPIC);

            if (topicSequence != null) {
                final String topicString = topicSequence.toString();

                if (this.topics.contains(topicString)) {
                    topic = topicSequence.toString();
                } else {
                    this.sendErrorResponse(context, streamId, apnsId, ErrorReason.TOPIC_DIALLOWED);
                    return;
                }
            } else {
                if (this.topics.size() == 1) {
                    topic = this.topics.iterator().next();
                } else {
                    this.sendErrorResponse(context, streamId, apnsId, ErrorReason.MISSING_TOPIC);
                    return;
                }
            }
        }

        {
            final CharSequence pathSequence = headers.get(Http2Headers.PseudoHeaderName.PATH.value());

            if (pathSequence != null) {
                final String pathString = pathSequence.toString();

                if (pathString.startsWith(PATH_PREFIX)) {
                    final String tokenString = pathString.substring(PATH_PREFIX.length());

                    final Matcher tokenMatcher = TOKEN_PATTERN.matcher(tokenString);

                    if (!tokenMatcher.matches()) {
                        this.sendErrorResponse(context, streamId, apnsId, ErrorReason.BAD_DEVICE_TOKEN);
                        return;
                    }

                    final Date expirationTimestamp = this.apnsServer.getExpirationTimestampForTokenInTopic(tokenString, topic);

                    if (expirationTimestamp != null) {
                        this.sendErrorResponse(context, streamId, apnsId, ErrorReason.UNREGISTERED, expirationTimestamp);
                    }

                    if (!this.apnsServer.isTokenRegisteredForTopic(tokenString, topic)) {
                        this.sendErrorResponse(context, streamId, apnsId, ErrorReason.DEVICE_TOKEN_NOT_FOR_TOPIC);
                        return;
                    }
                }
            } else {
                this.sendErrorResponse(context, streamId, apnsId, ErrorReason.BAD_PATH);
                return;
            }
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

    private void sendSuccessResponse(final ChannelHandlerContext context, final int streamId) {
        this.encoder().writeHeaders(context, streamId, SUCCESS_HEADERS, 0, true, context.newPromise());
        context.flush();
    }

    private void sendErrorResponse(final ChannelHandlerContext context, final int streamId, final UUID apnsId, final ErrorReason reason) {
        this.sendErrorResponse(context, streamId, apnsId, reason, null);
    }

    private void sendErrorResponse(final ChannelHandlerContext context, final int streamId, final UUID apnsId, final ErrorReason reason, final Date timestamp) {
        final Http2Headers headers = new DefaultHttp2Headers();
        headers.status(reason.getHttpResponseStatus().codeAsText());
        headers.add(HttpHeaderNames.CONTENT_TYPE, "application/json");

        if (apnsId != null) {
            headers.add(APNS_ID, apnsId.toString());
        }

        final byte[] payloadBytes;
        {
            final ErrorResponse errorResponse = new ErrorResponse(reason.getReasonText(), timestamp);
            payloadBytes = gson.toJson(errorResponse).getBytes();
        }

        this.encoder().writeHeaders(context, streamId, headers, 0, false, context.newPromise());
        this.encoder().writeData(context, streamId, Unpooled.wrappedBuffer(payloadBytes), 0, true, context.newPromise());

        context.flush();
    }
}
