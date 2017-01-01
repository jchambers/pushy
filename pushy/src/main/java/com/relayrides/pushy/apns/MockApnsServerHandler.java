package com.relayrides.pushy.apns;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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

    private final Map<Integer, UUID> requestsWaitingForDataFrame = new HashMap<>();

    private final ApnsKeyRegistry<ApnsVerificationKey> verificationKeyRegistry;
    private final Map<String, Date> expirationTimesByEncodedAuthenticationToken = new HashMap<>();
    private final Map<String, Set<String>> verifiedEncodedAuthenticationTokensByTopic = new HashMap<>();

    private static final Http2Headers SUCCESS_HEADERS = new DefaultHttp2Headers()
            .status(HttpResponseStatus.OK.codeAsText());

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_PRIORITY_HEADER = new AsciiString("apns-priority");
    private static final AsciiString APNS_ID_HEADER = new AsciiString("apns-id");
    private static final AsciiString APNS_AUTHORIZATION_HEADER = new AsciiString("authorization");

    private static final int MAX_CONTENT_LENGTH = 4096;
    private static final Pattern DEVIDE_TOKEN_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateAsTimeSinceEpochTypeAdapter(TimeUnit.MILLISECONDS))
            .create();

    private enum ErrorReason {
        BAD_COLLAPSE_ID("BadCollapseId", HttpResponseStatus.BAD_REQUEST),
        BAD_DEVICE_TOKEN("BadDeviceToken", HttpResponseStatus.BAD_REQUEST),
        BAD_EXPIRATION_DATE("BadExpirationDate", HttpResponseStatus.BAD_REQUEST),
        BAD_MESSAGE_ID("BadMessageId", HttpResponseStatus.BAD_REQUEST),
        BAD_PRIORITY("BadPriority", HttpResponseStatus.BAD_REQUEST),
        BAD_TOPIC("BadTopic", HttpResponseStatus.BAD_REQUEST),
        DEVICE_TOKEN_NOT_FOR_TOPIC("DeviceTokenNotForTopic", HttpResponseStatus.BAD_REQUEST),
        DUPLICATE_HEADERS("DuplicateHeaders", HttpResponseStatus.BAD_REQUEST),
        IDLE_TIMEOUT("IdleTimeout", HttpResponseStatus.BAD_REQUEST),
        MISSING_DEVICE_TOKEN("MissingDeviceToken", HttpResponseStatus.BAD_REQUEST),
        MISSING_TOPIC("MissingTopic", HttpResponseStatus.BAD_REQUEST),
        PAYLOAD_EMPTY("PayloadEmpty", HttpResponseStatus.BAD_REQUEST),
        TOPIC_DISALLOWED("TopicDisallowed", HttpResponseStatus.BAD_REQUEST),

        BAD_CERTIFICATE("BadCertificate", HttpResponseStatus.FORBIDDEN),
        BAD_CERTIFICATE_ENVIRONMENT("BadCertificateEnvironment", HttpResponseStatus.FORBIDDEN),
        EXPIRED_PROVIDER_TOKEN("ExpiredProviderToken", HttpResponseStatus.FORBIDDEN),
        FORBIDDEN("Forbidden", HttpResponseStatus.FORBIDDEN),
        INVALID_PROVIDER_TOKEN("InvalidProviderToken", HttpResponseStatus.FORBIDDEN),
        MISSING_PROVIDER_TOKEN("MissingProviderToken", HttpResponseStatus.FORBIDDEN),

        BAD_PATH("BadPath", HttpResponseStatus.NOT_FOUND),

        METHOD_NOT_ALLOWED("MethodNotAllowed", HttpResponseStatus.METHOD_NOT_ALLOWED),

        UNREGISTERED("Unregistered", HttpResponseStatus.GONE),

        PAYLOAD_TOO_LARGE("PayloadTooLarge", HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE),

        TOO_MANY_PROVIDER_TOKEN_UPDATES("TooManyProviderTokenUpdates", HttpResponseStatus.TOO_MANY_REQUESTS),
        TOO_MANY_REQUESTS("TooManyRequests", HttpResponseStatus.TOO_MANY_REQUESTS),

        INTERNAL_SERVER_ERROR("InternalServerError", HttpResponseStatus.INTERNAL_SERVER_ERROR),

        SERVICE_UNAVAILABLE("ServiceUnavailable", HttpResponseStatus.SERVICE_UNAVAILABLE),
        SHUTDOWN("Shutdown", HttpResponseStatus.SERVICE_UNAVAILABLE);

        private final String reasonText;
        private final HttpResponseStatus httpResponseStatus;

        private ErrorReason(final String reasonText, final HttpResponseStatus httpResponseStatus) {
            this.reasonText = reasonText;
            this.httpResponseStatus = httpResponseStatus;
        }

        public String getReasonText() {
            return this.reasonText;
        }

        public HttpResponseStatus getHttpResponseStatus() {
            return this.httpResponseStatus;
        }
    }

    public static final class MockApnsServerHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<MockApnsServerHandler, MockApnsServerHandlerBuilder> {
        private MockApnsServer apnsServer;
        private ApnsKeyRegistry<ApnsVerificationKey> verificationKeyRegistry;

        public MockApnsServerHandlerBuilder apnsServer(final MockApnsServer apnsServer) {
            this.apnsServer = apnsServer;
            return this;
        }

        public MockApnsServer apnsServer() {
            return this.apnsServer;
        }

        public MockApnsServerHandlerBuilder verificationKeyRegistry(final ApnsKeyRegistry<ApnsVerificationKey> verificationKeyRegistry) {
            this.verificationKeyRegistry = verificationKeyRegistry;
            return this;
        }

        public ApnsKeyRegistry<ApnsVerificationKey> verificationKeyRegistry() {
            return this.verificationKeyRegistry;
        }

        @Override
        public MockApnsServerHandlerBuilder initialSettings(final Http2Settings initialSettings) {
            return super.initialSettings(initialSettings);
        }

        @Override
        public MockApnsServerHandler build(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
            final MockApnsServerHandler handler = new MockApnsServerHandler(decoder, encoder, initialSettings, this.apnsServer(), this.verificationKeyRegistry());
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

    private static class InternalServerErrorResponse {
        private final int streamId;

        public InternalServerErrorResponse(final int streamId) {
            this.streamId = streamId;
        }

        public int getStreamId() {
            return this.streamId;
        }
    }

    protected MockApnsServerHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final MockApnsServer apnsServer, final ApnsKeyRegistry<ApnsVerificationKey> verificationKeyRegistry) {
        super(decoder, encoder, initialSettings);

        this.apnsServer = apnsServer;
        this.verificationKeyRegistry = verificationKeyRegistry;
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
        if (this.apnsServer.shouldEmulateInternalErrors()) {
            context.channel().writeAndFlush(new InternalServerErrorResponse(streamId));
            return;
        }

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

        final String topic;
        {
            final CharSequence topicSequence = headers.get(APNS_TOPIC_HEADER);
            topic = (topicSequence != null) ? topicSequence.toString() : null;
        }

        if (topic == null) {
            context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.MISSING_TOPIC));
            return;
        }

        final String encodedAuthenticationToken;
        {
            final CharSequence authorizationSequence = headers.get(APNS_AUTHORIZATION_HEADER);

            if (authorizationSequence != null) {
                final String authorizationString = authorizationSequence.toString();

                if (authorizationString.startsWith("bearer")) {
                    encodedAuthenticationToken = authorizationString.substring("bearer".length()).trim();
                } else {
                    encodedAuthenticationToken = null;
                }
            } else {
                encodedAuthenticationToken = null;
            }
        }

        if (encodedAuthenticationToken == null) {
            context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.MISSING_PROVIDER_TOKEN));
            return;
        }

        final Set<String> verifiedTokensForTopic = this.verifiedEncodedAuthenticationTokensByTopic.get(topic);

        if (verifiedTokensForTopic != null && verifiedTokensForTopic.contains(encodedAuthenticationToken)) {
            // We've previously verified the signature for the given encoded token, but it may have expired since then.
            final Date tokenExpiration = this.expirationTimesByEncodedAuthenticationToken.get(encodedAuthenticationToken);

            if (new Date().after(tokenExpiration)) {
                verifiedTokensForTopic.remove(encodedAuthenticationToken);
                this.expirationTimesByEncodedAuthenticationToken.remove(encodedAuthenticationToken);

                context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.EXPIRED_PROVIDER_TOKEN));
                return;
            }
        } else {
            // We don't trust this token yet and need to verify it.
            final ApnsVerificationKey verificationKey = this.verificationKeyRegistry.getKeyForTopic(topic);

            if (verificationKey == null) {
                context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.BAD_TOPIC));
                return;
            }

            final AuthenticationToken authenticationToken = new AuthenticationToken(encodedAuthenticationToken);

            if (new Date().after(authenticationToken.getExpiration())) {
                context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.EXPIRED_PROVIDER_TOKEN));
                return;
            }

            try {
                if (!authenticationToken.verifySignature(verificationKey)) {
                    context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.INVALID_PROVIDER_TOKEN));
                    return;
                }
            } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
                context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.INVALID_PROVIDER_TOKEN));
                return;
            }

            if (!this.verifiedEncodedAuthenticationTokensByTopic.containsKey(topic)) {
                this.verifiedEncodedAuthenticationTokensByTopic.put(topic, new HashSet<String>());
            }

            this.verifiedEncodedAuthenticationTokensByTopic.get(topic).add(encodedAuthenticationToken);
            this.expirationTimesByEncodedAuthenticationToken.put(encodedAuthenticationToken, authenticationToken.getExpiration());
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
                    final String deviceTokenString = pathString.substring(APNS_PATH_PREFIX.length());

                    final Matcher deviceTokenMatcher = DEVIDE_TOKEN_PATTERN.matcher(deviceTokenString);

                    if (!deviceTokenMatcher.matches()) {
                        context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.BAD_DEVICE_TOKEN));
                        return;
                    }

                    final Date expirationTimestamp = this.apnsServer.getExpirationTimestampForTokenInTopic(deviceTokenString, topic);

                    if (expirationTimestamp != null) {
                        context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.UNREGISTERED, expirationTimestamp));
                        return;
                    }

                    if (!this.apnsServer.isTokenRegisteredForTopic(deviceTokenString, topic)) {
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

                payloadBytes = GSON.toJson(errorResponse).getBytes();
            }

            final ChannelPromise headersPromise = context.newPromise();
            this.encoder().writeHeaders(context, rejectNotificationResponse.getStreamId(), headers, 0, false, headersPromise);

            final ChannelPromise dataPromise = context.newPromise();
            this.encoder().writeData(context, rejectNotificationResponse.getStreamId(), Unpooled.wrappedBuffer(payloadBytes), 0, true, dataPromise);

            final PromiseCombiner promiseCombiner = new PromiseCombiner();
            promiseCombiner.addAll(headersPromise, dataPromise);
            promiseCombiner.finish(writePromise);
        } else if (message instanceof InternalServerErrorResponse) {
            final InternalServerErrorResponse internalServerErrorResponse = (InternalServerErrorResponse) message;

            final Http2Headers headers = new DefaultHttp2Headers();
            headers.status(HttpResponseStatus.INTERNAL_SERVER_ERROR.codeAsText());

            this.encoder().writeHeaders(context, internalServerErrorResponse.getStreamId(), headers, 0, true, writePromise);
        } else {
            context.write(message, writePromise);
        }
    }
}
