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
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.relayrides.pushy.apns.auth.ApnsKeyRemovalListener;
import com.relayrides.pushy.apns.auth.ApnsKeySource;
import com.relayrides.pushy.apns.auth.ApnsVerificationKey;

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
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;

class MockApnsServerHandler extends Http2ConnectionHandler implements Http2FrameListener, ApnsKeyRemovalListener<ApnsVerificationKey> {

    private volatile EventExecutor eventExecutor;

    private final Map<Integer, Http2Headers> headersByStreamId = new HashMap<>();

    private Map<String, Map<String, Date>> deviceTokenExpirationsByTopic;

    private final ApnsKeySource<ApnsVerificationKey> verificationKeySource;
    private final Map<ApnsVerificationKey, Set<String>> topicsByVerificationKey = new HashMap<>();
    private final Map<String, Date> expirationTimesByEncodedAuthenticationToken = new WeakHashMap<>();
    private final Map<String, Set<String>> verifiedEncodedAuthenticationTokensByTopic = new HashMap<>();

    private boolean emulateInternalServerErrors;

    private static final Http2Headers SUCCESS_HEADERS = new DefaultHttp2Headers()
            .status(HttpResponseStatus.OK.codeAsText());

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_PRIORITY_HEADER = new AsciiString("apns-priority");
    private static final AsciiString APNS_ID_HEADER = new AsciiString("apns-id");
    private static final AsciiString APNS_AUTHORIZATION_HEADER = new AsciiString("authorization");

    private static final int MAX_CONTENT_LENGTH = 4096;
    private static final Pattern DEVIDE_TOKEN_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

    private static final Logger log = LoggerFactory.getLogger(MockApnsServerHandler.class);

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
        private MockApnsServerHandlerConfiguration initialHandlerConfiguration;
        private ApnsKeySource<ApnsVerificationKey> verificationKeySource;

        public MockApnsServerHandlerBuilder initialHandlerConfiguration(final MockApnsServerHandlerConfiguration initialHandlerConfiguration) {
            this.initialHandlerConfiguration = initialHandlerConfiguration;
            return this;
        }

        public MockApnsServerHandlerConfiguration initialHandlerConfiguration() {
            return this.initialHandlerConfiguration;
        }

        public MockApnsServerHandlerBuilder verificationKeySource(final ApnsKeySource<ApnsVerificationKey> verificationKeySource) {
            this.verificationKeySource = verificationKeySource;
            return this;
        }

        public ApnsKeySource<ApnsVerificationKey> verificationKeySource() {
            return this.verificationKeySource;
        }

        @Override
        public MockApnsServerHandlerBuilder initialSettings(final Http2Settings initialSettings) {
            // This method isn't externally-visible by default; we're just exposing it to outside callers here.
            return super.initialSettings(initialSettings);
        }

        @Override
        public MockApnsServerHandler build(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
            final MockApnsServerHandler handler = new MockApnsServerHandler(decoder, encoder, initialSettings, this.initialHandlerConfiguration(), this.verificationKeySource());
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

    private static class InvalidAuthenticationTokenException extends Exception {
        private static final long serialVersionUID = 1L;

        private final ErrorReason errorReason;

        public InvalidAuthenticationTokenException(final ErrorReason errorReason) {
            this.errorReason = errorReason;
        }

        public ErrorReason getErrorReason() {
            return this.errorReason;
        }
    }

    private static class RejectedNotificationException extends Exception {
        private static final long serialVersionUID = 1L;

        private final UUID apnsId;
        private final ErrorReason errorReason;
        private final Date timestamp;

        public RejectedNotificationException(final UUID apnsId, final ErrorReason errorReason) {
            this(apnsId, errorReason, null);
        }

        public RejectedNotificationException(final UUID apnsId, final ErrorReason errorReason, final Date timestamp) {
            this.apnsId = apnsId;
            this.errorReason = errorReason;
            this.timestamp = timestamp;
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

    protected MockApnsServerHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final MockApnsServerHandlerConfiguration initialHandlerConfiguration, final ApnsKeySource<ApnsVerificationKey> verificationKeySource) {
        super(decoder, encoder, initialSettings);

        this.emulateInternalServerErrors = initialHandlerConfiguration.shouldEmulateInternalServerErrors();
        this.deviceTokenExpirationsByTopic = initialHandlerConfiguration.getTokenExpirationsByTopic();

        this.verificationKeySource = verificationKeySource;
    }

    @Override
    public void channelActive(final ChannelHandlerContext context) throws Exception {
        super.channelActive(context);

        this.eventExecutor = context.executor();
        this.verificationKeySource.addKeyRemovalListener(this);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext context) throws Exception {
        super.channelInactive(context);

        this.verificationKeySource.removeKeyRemovalListener(this);
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers, final int streamDependency,
            final short weight, final boolean exclusive, final int padding, final boolean endOfStream) throws Http2Exception {

        this.onHeadersRead(ctx, streamId, headers, padding, endOfStream);
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) throws Http2Exception {
        if (endOfStream) {
            // It looks like we don't have any data coming, so process (and presumably reject) this notification
            // immediately.
            this.processPushNotification(context, streamId, headers, null);
        } else {
            // Store the headers so we can process them when the data arrives.
            this.headersByStreamId.put(streamId, headers);
        }
    }

    @Override
    public int onDataRead(final ChannelHandlerContext context, final int streamId, final ByteBuf data, final int padding, final boolean endOfStream) throws Http2Exception {
        final int bytesProcessed = data.readableBytes() + padding;

        if (endOfStream) {
            this.processPushNotification(context, streamId, this.headersByStreamId.remove(streamId), data);
        } else {
            log.error("Received a `DATA` frame on stream {} that was not the end of the stream.", streamId);
        }

        return bytesProcessed;
    }

    private void processPushNotification(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final ByteBuf data) {
        if (this.emulateInternalServerErrors) {
            context.channel().writeAndFlush(new InternalServerErrorResponse(streamId));
        } else {
            try {
                if (!HttpMethod.POST.asciiName().contentEquals(headers.get(Http2Headers.PseudoHeaderName.METHOD.value()))) {
                    throw new RejectedNotificationException(null, ErrorReason.METHOD_NOT_ALLOWED);
                }

                final UUID apnsId;
                {
                    final CharSequence apnsIdSequence = headers.get(APNS_ID_HEADER);

                    if (apnsIdSequence != null) {
                        try {
                            apnsId = UUID.fromString(apnsIdSequence.toString());
                        } catch (final IllegalArgumentException e) {
                            throw new RejectedNotificationException(null, ErrorReason.BAD_MESSAGE_ID);
                        }
                    } else {
                        // If the client didn't send us a UUID, make one up (for now)
                        apnsId = UUID.randomUUID();
                    }
                }

                final String topic;
                {
                    final CharSequence topicSequence = headers.get(APNS_TOPIC_HEADER);
                    topic = (topicSequence != null) ? topicSequence.toString() : null;
                }

                if (topic == null) {
                    throw new RejectedNotificationException(apnsId, ErrorReason.MISSING_TOPIC);
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
                    throw new RejectedNotificationException(apnsId, ErrorReason.MISSING_PROVIDER_TOKEN);
                }

                final DefaultPromise<Void> signatureVerificationPromise = new DefaultPromise<>(context.executor());
                this.verifyAuthenticationToken(encodedAuthenticationToken, topic, signatureVerificationPromise, context.executor());

                signatureVerificationPromise.addListener(new GenericFutureListener<Future<Void>>() {

                    @Override
                    public void operationComplete(final Future<Void> signatureVerificationFuture) throws Exception {
                        try {
                            if (signatureVerificationFuture.isSuccess()) {
                                {
                                    final Integer priorityCode = headers.getInt(APNS_PRIORITY_HEADER);

                                    if (priorityCode != null) {
                                        try {
                                            DeliveryPriority.getFromCode(priorityCode);
                                        } catch (final IllegalArgumentException e) {
                                            throw new RejectedNotificationException(apnsId, ErrorReason.BAD_PRIORITY);
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
                                                throw new RejectedNotificationException(apnsId, ErrorReason.BAD_DEVICE_TOKEN);
                                            }

                                            final Date expirationTimestamp;
                                            {
                                                final Map<String, Date> tokensWithinTopic = MockApnsServerHandler.this.deviceTokenExpirationsByTopic.get(topic);
                                                expirationTimestamp = tokensWithinTopic != null ? tokensWithinTopic.get(deviceTokenString) : null;
                                            }

                                            if (expirationTimestamp != null) {
                                                throw new RejectedNotificationException(apnsId, ErrorReason.UNREGISTERED, expirationTimestamp);
                                            }

                                            final boolean deviceTokenIsRegisteredForTopic;
                                            {
                                                final Map<String, Date> tokensWithinTopic = MockApnsServerHandler.this.deviceTokenExpirationsByTopic.get(topic);
                                                deviceTokenIsRegisteredForTopic = tokensWithinTopic != null && tokensWithinTopic.containsKey(deviceTokenString);
                                            }

                                            if (!deviceTokenIsRegisteredForTopic) {
                                                throw new RejectedNotificationException(apnsId, ErrorReason.DEVICE_TOKEN_NOT_FOR_TOPIC);
                                            }
                                        }
                                    } else {
                                        throw new RejectedNotificationException(apnsId, ErrorReason.BAD_PATH);
                                    }
                                }

                                if (data == null) {
                                    throw new RejectedNotificationException(apnsId, ErrorReason.PAYLOAD_EMPTY);
                                } else {
                                    if (data.readableBytes() > MAX_CONTENT_LENGTH) {
                                        throw new RejectedNotificationException(apnsId, ErrorReason.PAYLOAD_TOO_LARGE);
                                    }
                                }

                                context.channel().writeAndFlush(new AcceptNotificationResponse(streamId));
                            } else {
                                assert signatureVerificationFuture.cause() instanceof InvalidAuthenticationTokenException;

                                if (signatureVerificationFuture.cause() instanceof InvalidAuthenticationTokenException) {
                                    InvalidAuthenticationTokenException cause = (InvalidAuthenticationTokenException) signatureVerificationFuture.cause();
                                    throw new RejectedNotificationException(apnsId, cause.getErrorReason());
                                } else {
                                    throw new RejectedNotificationException(apnsId, ErrorReason.INVALID_PROVIDER_TOKEN);
                                }
                            }
                        } catch (RejectedNotificationException e) {
                            context.channel().writeAndFlush(new RejectNotificationResponse(streamId, e.getApnsId(), e.getErrorReason(), e.getTimestamp()));
                        }
                    }
                });
            } catch (RejectedNotificationException e) {
                context.channel().writeAndFlush(new RejectNotificationResponse(streamId, e.getApnsId(), e.getErrorReason(), e.getTimestamp()));
            }
        }
    }

    private void verifyAuthenticationToken(final String encodedAuthenticationToken, final String topic, final Promise<Void> verificationPromise, final EventExecutor executor) {
        final Set<String> verifiedTokensForTopic = this.verifiedEncodedAuthenticationTokensByTopic.get(topic);

        if (verifiedTokensForTopic != null && verifiedTokensForTopic.contains(encodedAuthenticationToken)) {
            // We've previously verified the signature for the given encoded token, but it may have expired
            // since then.
            final Date tokenExpiration = this.expirationTimesByEncodedAuthenticationToken.get(encodedAuthenticationToken);

            if (new Date().after(tokenExpiration)) {
                verifiedTokensForTopic.remove(encodedAuthenticationToken);
                verificationPromise.tryFailure(new InvalidAuthenticationTokenException(ErrorReason.EXPIRED_PROVIDER_TOKEN));
            } else {
                verificationPromise.trySuccess(null);
            }
        } else {
            // We don't trust this token yet and need to verify it.
            final DefaultPromise<ApnsVerificationKey> keyPromise = new DefaultPromise<>(executor);
            this.verificationKeySource.getKeyForTopic(topic, keyPromise);

            keyPromise.addListener(new GenericFutureListener<Future<ApnsVerificationKey>>() {

                @Override
                public void operationComplete(final Future<ApnsVerificationKey> keyFuture) {
                    if (keyFuture.isSuccess()) {
                        final AuthenticationToken authenticationToken = new AuthenticationToken(encodedAuthenticationToken);

                        if (new Date().after(authenticationToken.getExpiration())) {
                            verificationPromise.tryFailure(new InvalidAuthenticationTokenException(ErrorReason.EXPIRED_PROVIDER_TOKEN));
                        }

                        final ApnsVerificationKey verificationKey = keyFuture.getNow();

                        try {
                            if (!authenticationToken.verifySignature(verificationKey)) {
                                verificationPromise.tryFailure(new InvalidAuthenticationTokenException(ErrorReason.INVALID_PROVIDER_TOKEN));
                            }
                        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
                            verificationPromise.tryFailure(new InvalidAuthenticationTokenException(ErrorReason.INVALID_PROVIDER_TOKEN));
                        }

                        if (!MockApnsServerHandler.this.verifiedEncodedAuthenticationTokensByTopic.containsKey(topic)) {
                            MockApnsServerHandler.this.verifiedEncodedAuthenticationTokensByTopic.put(topic, new HashSet<String>());
                        }

                        MockApnsServerHandler.this.verifiedEncodedAuthenticationTokensByTopic.get(topic).add(encodedAuthenticationToken);
                        MockApnsServerHandler.this.expirationTimesByEncodedAuthenticationToken.put(encodedAuthenticationToken, authenticationToken.getExpiration());

                        if (!MockApnsServerHandler.this.topicsByVerificationKey.containsKey(verificationKey)) {
                            MockApnsServerHandler.this.topicsByVerificationKey.put(verificationKey, new HashSet<String>());
                        }

                        MockApnsServerHandler.this.topicsByVerificationKey.get(verificationKey).add(topic);

                        verificationPromise.trySuccess(null);
                    } else {
                        verificationPromise.tryFailure(new InvalidAuthenticationTokenException(ErrorReason.BAD_TOPIC));
                    }
                }
            });
        }
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

    @Override
    public void userEventTriggered(final ChannelHandlerContext context, final Object event) throws Exception {
        if (event instanceof MockApnsServerHandlerConfiguration) {
            final MockApnsServerHandlerConfiguration configuration = (MockApnsServerHandlerConfiguration) event;

            this.emulateInternalServerErrors = configuration.shouldEmulateInternalServerErrors();
            this.deviceTokenExpirationsByTopic = configuration.getTokenExpirationsByTopic();
        } else {
            context.fireUserEventTriggered(event);
        }
    }

    @Override
    public void handleKeyRemoval(final ApnsVerificationKey key) {
        assert this.eventExecutor != null;

        this.eventExecutor.execute(new Runnable() {

            @Override
            public void run() {
                final Set<String> topicsForRemovedKey = MockApnsServerHandler.this.topicsByVerificationKey.remove(key);

                if (topicsForRemovedKey != null) {
                    for (final String topic : topicsForRemovedKey) {
                        final Set<String> encodedAuthenticationTokens = MockApnsServerHandler.this.verifiedEncodedAuthenticationTokensByTopic.remove(topic);

                        if (encodedAuthenticationTokens != null) {
                            for (final String encodedAuthenticationToken : encodedAuthenticationTokens) {
                                MockApnsServerHandler.this.expirationTimesByEncodedAuthenticationToken.remove(encodedAuthenticationToken);
                            }
                        }
                    }
                }
            }
        });
    }
}
