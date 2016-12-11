package com.relayrides.pushy.apns;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
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
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.base64.Base64Dialect;
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
    private final boolean useTokenAuthentication;
    private final Set<String> topicsFromClientCertificate;

    private final Map<Integer, UUID> requestsWaitingForDataFrame = new HashMap<>();

    private final Map<String, Date> authenticationTokenExpirationTimes = new HashMap<>();
    private final Map<String, Set<String>> verifiedAuthenticationTokensByTopic = new HashMap<>();

    private static final Http2Headers SUCCESS_HEADERS = new DefaultHttp2Headers()
            .status(HttpResponseStatus.OK.codeAsText());

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_PRIORITY_HEADER = new AsciiString("apns-priority");
    private static final AsciiString APNS_ID_HEADER = new AsciiString("apns-id");
    private static final AsciiString APNS_AUTHORIZATION_HEADER = new AsciiString("authorization");

    private static final int MAX_CONTENT_LENGTH = 4096;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

    private static final long AUTH_TOKEN_EXPIRATION_MILLIS = TimeUnit.HOURS.toMillis(1);

    private static final Gson PAYLOAD_GSON = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateAsTimeSinceEpochTypeAdapter(TimeUnit.MILLISECONDS))
            .create();

    private static final Gson AUTH_GSON = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateAsTimeSinceEpochTypeAdapter(TimeUnit.SECONDS))
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

    private static class ExpiredAuthenticationTokenException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static class InvalidAuthenticationTokenException extends Exception {
        private static final long serialVersionUID = 1L;

        public InvalidAuthenticationTokenException() {
            super();
        }

        public InvalidAuthenticationTokenException(final Throwable cause) {
            super(cause);
        }
    }

    public static final class MockApnsServerHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<MockApnsServerHandler, MockApnsServerHandlerBuilder> {
        private MockApnsServer apnsServer;
        private boolean useTokenAuthentication;
        private String baseTopicFromCertificate;

        public MockApnsServerHandlerBuilder apnsServer(final MockApnsServer apnsServer) {
            this.apnsServer = apnsServer;
            return this;
        }

        public MockApnsServer apnsServer() {
            return this.apnsServer;
        }

        public MockApnsServerHandlerBuilder useTokenAuthentication(final boolean useTokenAuthentication) {
            this.useTokenAuthentication = useTokenAuthentication;
            return this;
        }

        public boolean useTokenAuthentication() {
            return this.useTokenAuthentication;
        }

        public MockApnsServerHandlerBuilder baseTopicFromCertificate(final String baseTopicFromCertificate) {
            this.baseTopicFromCertificate = baseTopicFromCertificate;
            return this;
        }

        public String baseTopicFromCertificate() {
            return this.baseTopicFromCertificate;
        }

        @Override
        public MockApnsServerHandlerBuilder initialSettings(final Http2Settings initialSettings) {
            return super.initialSettings(initialSettings);
        }

        @Override
        public MockApnsServerHandler build(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
            final MockApnsServerHandler handler = new MockApnsServerHandler(decoder, encoder, initialSettings, this.apnsServer(), this.useTokenAuthentication(), this.baseTopicFromCertificate());
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

    protected MockApnsServerHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final MockApnsServer apnsServer, final boolean useTokenAuthentication, final String baseTopicFromCertificate) {
        super(decoder, encoder, initialSettings);

        this.apnsServer = apnsServer;
        this.useTokenAuthentication = useTokenAuthentication;

        this.topicsFromClientCertificate = new HashSet<>();

        if (baseTopicFromCertificate != null) {
            this.topicsFromClientCertificate.add(baseTopicFromCertificate);
            this.topicsFromClientCertificate.add(baseTopicFromCertificate + ".voip");
            this.topicsFromClientCertificate.add(baseTopicFromCertificate + ".complication");
        }
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

        final String authenticationToken;
        {
            final CharSequence authorizationSequence = headers.get(APNS_AUTHORIZATION_HEADER);

            if (authorizationSequence != null) {
                final String authorizationString = authorizationSequence.toString();

                if (authorizationString.startsWith("bearer")) {
                    authenticationToken = authorizationString.substring("bearer".length()).trim();
                } else {
                    authenticationToken = null;
                }
            } else {
                authenticationToken = null;
            }
        }

        final String topic;
        {
            final CharSequence topicSequence = headers.get(APNS_TOPIC_HEADER);
            topic = (topicSequence != null) ? topicSequence.toString() : null;
        }

        if (this.useTokenAuthentication) {
            final Set<String> verifiedTokensForTopic = this.verifiedAuthenticationTokensByTopic.get(topic);

            if (verifiedTokensForTopic != null && verifiedTokensForTopic.contains(authenticationToken)) {
                final Date tokenExpiration = this.authenticationTokenExpirationTimes.get(authenticationToken);
                final Date now = new Date();

                if (now.after(tokenExpiration)) {
                    verifiedTokensForTopic.remove(authenticationToken);
                    this.authenticationTokenExpirationTimes.remove(authenticationToken);

                    context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.EXPIRED_PROVIDER_TOKEN));
                    return;
                }
            } else {
                try {
                    final AuthenticationTokenClaims claims = this.getVerifiedClaimsFromAuthenticationToken(authenticationToken);
                    final Set<String> topics = this.apnsServer.getTopicsForTeamId(claims.getIssuer());

                    if (topics == null || !topics.contains(topic)) {
                        context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.DEVICE_TOKEN_NOT_FOR_TOPIC));
                        return;
                    }

                    if (!this.verifiedAuthenticationTokensByTopic.containsKey(topic)) {
                        this.verifiedAuthenticationTokensByTopic.put(topic, new HashSet<String>());
                    }

                    this.verifiedAuthenticationTokensByTopic.get(topic).add(authenticationToken);
                    this.authenticationTokenExpirationTimes.put(authenticationToken, new Date(System.currentTimeMillis() + AUTH_TOKEN_EXPIRATION_MILLIS));
                } catch (final InvalidAuthenticationTokenException e) {
                    context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.INVALID_PROVIDER_TOKEN));
                    return;
                } catch (final ExpiredAuthenticationTokenException e) {
                    context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.EXPIRED_PROVIDER_TOKEN));
                    return;
                }
            }
        } else {
            if (topic != null) {
                if (!this.topicsFromClientCertificate.contains(topic)) {
                    context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.BAD_TOPIC));
                    return;
                }
            } else {
                context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.MISSING_TOPIC));
                return;
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

                payloadBytes = PAYLOAD_GSON.toJson(errorResponse).getBytes();
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

    protected AuthenticationTokenClaims getVerifiedClaimsFromAuthenticationToken(final String authenticationToken) throws InvalidAuthenticationTokenException, ExpiredAuthenticationTokenException {
        if (authenticationToken == null) {
            throw new InvalidAuthenticationTokenException();
        }

        final ByteBuf tokenBuffer = Unpooled.wrappedBuffer(authenticationToken.getBytes(StandardCharsets.US_ASCII));

        final AuthenticationTokenHeader header;
        final AuthenticationTokenClaims claims;
        final byte[] headerAndClaimsBytes;
        final byte[] expectedSignature;

        try {
            {
                final ByteBuf decodedHeaderBuffer = this.padAndBase64Decode(tokenBuffer.readSlice(tokenBuffer.bytesBefore((byte) '.')));

                header = AUTH_GSON.fromJson(decodedHeaderBuffer.toString(StandardCharsets.US_ASCII),
                        AuthenticationTokenHeader.class);

                decodedHeaderBuffer.release();
            }

            tokenBuffer.skipBytes(1);

            {
                final ByteBuf decodedClaimsBuffer = this.padAndBase64Decode(tokenBuffer.readSlice(tokenBuffer.bytesBefore((byte) '.')));

                claims = AUTH_GSON.fromJson(decodedClaimsBuffer.toString(StandardCharsets.US_ASCII),
                        AuthenticationTokenClaims.class);

                decodedClaimsBuffer.release();
            }

            headerAndClaimsBytes = new byte[tokenBuffer.readerIndex()];
            tokenBuffer.getBytes(0, headerAndClaimsBytes, 0, tokenBuffer.readerIndex());

            tokenBuffer.skipBytes(1);

            {
                final ByteBuf decodedSignatureBuffer = this.padAndBase64Decode(tokenBuffer);

                expectedSignature = new byte[decodedSignatureBuffer.readableBytes()];
                decodedSignatureBuffer.readBytes(expectedSignature);

                decodedSignatureBuffer.release();
            }
        } catch (final RuntimeException e) {
            e.printStackTrace();
            throw new InvalidAuthenticationTokenException(e);
        } finally {
            tokenBuffer.release();
        }

        final Date oldestAllowableIssueDate = new Date(System.currentTimeMillis() - AUTH_TOKEN_EXPIRATION_MILLIS);

        if (claims.getIssuedAt().before(oldestAllowableIssueDate)) {
            throw new ExpiredAuthenticationTokenException();
        }

        final Signature signature = this.apnsServer.getSignatureForKeyId(header.getKeyId());

        if (signature == null) {
            throw new InvalidAuthenticationTokenException();
        }

        try {
            signature.update(headerAndClaimsBytes);

            if (!signature.verify(expectedSignature)) {
                throw new InvalidAuthenticationTokenException();
            }
        } catch (final SignatureException e) {
            throw new InvalidAuthenticationTokenException(e);
        }

        // At this point, we know that the claims and header were signed by the private key identified in the header,
        // but we still need to verify that the key belongs to the team named in the claims.
        final String teamId = this.apnsServer.getTeamIdForKeyId(header.getKeyId());

        if (!claims.getIssuer().equals(teamId)) {
            throw new InvalidAuthenticationTokenException();
        }

        return claims;
    }

    private ByteBuf padAndBase64Decode(final ByteBuf source) {
        final int paddedLength = source.readableBytes() + 2;
        final ByteBuf paddedSource = source.alloc().heapBuffer(paddedLength, paddedLength);

        source.getBytes(source.readerIndex(), paddedSource, source.readableBytes());

        switch (source.readableBytes() % 4) {
            case 2: {
                paddedSource.writeByte('=');
                paddedSource.writeByte('=');
                break;
            }

            case 3: {
                paddedSource.writeByte('=');
                break;
            }
        }

        final ByteBuf decoded = Base64.decode(paddedSource, Base64Dialect.URL_SAFE);

        paddedSource.release();

        return decoded;
    }
}
