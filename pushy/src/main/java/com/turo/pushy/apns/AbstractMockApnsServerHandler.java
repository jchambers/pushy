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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.*;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.PromiseCombiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class AbstractMockApnsServerHandler extends Http2ConnectionHandler implements Http2FrameListener {

    private final boolean emulateInternalErrors;

    private final Http2Connection.PropertyKey apnsIdPropertyKey;

    private final Map<String, Map<String, Date>> deviceTokenExpirationsByTopic;

    private static final Http2Headers SUCCESS_HEADERS = new DefaultHttp2Headers().status(HttpResponseStatus.OK.codeAsText());

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_PRIORITY_HEADER = new AsciiString("apns-priority");
    private static final AsciiString APNS_ID_HEADER = new AsciiString("apns-id");

    private static final int MAX_CONTENT_LENGTH = 4096;
    private static final Pattern DEVICE_TOKEN_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateAsTimeSinceEpochTypeAdapter(TimeUnit.MILLISECONDS))
            .create();

    private static final Logger log = LoggerFactory.getLogger(AbstractMockApnsServerHandler.class);

    protected enum ErrorReason {
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

    static class RejectedNotificationException extends Exception {
        private final ErrorReason errorReason;
        private final Date deviceTokenExpirationTimestamp;

        public RejectedNotificationException(final ErrorReason errorReason) {
            this(errorReason, null);
        }

        public RejectedNotificationException(final ErrorReason errorReason, final Date deviceTokenExpirationTimestamp) {
            Objects.requireNonNull(errorReason, "Error reason must not be null.");

            this.errorReason = errorReason;
            this.deviceTokenExpirationTimestamp = deviceTokenExpirationTimestamp;
        }

        public ErrorReason getErrorReason() {
            return errorReason;
        }

        public Date getDeviceTokenExpirationTimestamp() {
            return deviceTokenExpirationTimestamp;
        }
    }

    public static abstract class AbstractMockApnsServerHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<AbstractMockApnsServerHandler, AbstractMockApnsServerHandlerBuilder> {

        private boolean emulateInternalErrors;

        private Map<String, Map<String, Date>> deviceTokenExpirationsByTopic;

        @Override
        public AbstractMockApnsServerHandlerBuilder initialSettings(final Http2Settings initialSettings) {
            return super.initialSettings(initialSettings);
        }

        public AbstractMockApnsServerHandlerBuilder emulateInternalErrors(final boolean emulateInternalErrors) {
            this.emulateInternalErrors = emulateInternalErrors;
            return this;
        }

        public boolean emulateInternalErrors() {
            return this.emulateInternalErrors;
        }

        public AbstractMockApnsServerHandlerBuilder deviceTokenExpirationsByTopic(final Map<String, Map<String, Date>> deviceTokenExpirationsByTopic) {
            this.deviceTokenExpirationsByTopic = deviceTokenExpirationsByTopic;
            return this;
        }

        public Map<String, Map<String, Date>> deviceTokenExpirationsByTopic() {
            return this.deviceTokenExpirationsByTopic;
        }

        @Override
        public AbstractMockApnsServerHandler build() {
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

    protected AbstractMockApnsServerHandler(final Http2ConnectionDecoder decoder,
                                            final Http2ConnectionEncoder encoder,
                                            final Http2Settings initialSettings,
                                            final boolean emulateInternalErrors,
                                            final Map<String, Map<String, Date>> deviceTokenExpirationsByTopic) {

        super(decoder, encoder, initialSettings);

        this.apnsIdPropertyKey = this.connection().newKey();

        this.emulateInternalErrors = emulateInternalErrors;
        this.deviceTokenExpirationsByTopic = deviceTokenExpirationsByTopic;
    }

    @Override
    public int onDataRead(final ChannelHandlerContext context, final int streamId, final ByteBuf data, final int padding, final boolean endOfStream) throws Http2Exception {
        final int bytesProcessed = data.readableBytes() + padding;

        if (endOfStream) {
            final Http2Stream stream = this.connection().stream(streamId);

            // Presumably, we spotted an error earlier and sent a response immediately if the stream is closed on our
            // side.
            if (stream.state() == Http2Stream.State.OPEN) {
                final UUID apnsId = stream.getProperty(this.apnsIdPropertyKey);

                if (data.readableBytes() <= MAX_CONTENT_LENGTH) {
                    context.channel().writeAndFlush(new AcceptNotificationResponse(streamId));
                } else {
                    context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, ErrorReason.PAYLOAD_TOO_LARGE, null));
                }
            }
        }

        return bytesProcessed;
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) throws Http2Exception {
        if (this.emulateInternalErrors) {
            context.channel().writeAndFlush(new InternalServerErrorResponse(streamId));
        } else {
            final Http2Stream stream = this.connection().stream(streamId);

            UUID apnsId = null;

            try {
                {
                    final CharSequence apnsIdSequence = headers.get(APNS_ID_HEADER);

                    if (apnsIdSequence != null) {
                        try {
                            apnsId = UUID.fromString(apnsIdSequence.toString());
                        } catch (final IllegalArgumentException e) {
                            throw new RejectedNotificationException(ErrorReason.BAD_MESSAGE_ID);
                        }
                    } else {
                        // If the client didn't send us a UUID, make one up (for now)
                        apnsId = UUID.randomUUID();
                    }
                }

                if (!HttpMethod.POST.asciiName().contentEquals(headers.get(Http2Headers.PseudoHeaderName.METHOD.value()))) {
                    throw new RejectedNotificationException(ErrorReason.METHOD_NOT_ALLOWED);
                }

                if (endOfStream) {
                    throw new RejectedNotificationException(ErrorReason.PAYLOAD_EMPTY);
                }

                this.verifyHeaders(headers);

                // At this point, we've made it through all of the headers without an exception and know we're waiting
                // for a data frame. The data frame handler will want the APNs ID in case it needs to send an error
                // response.
                stream.setProperty(this.apnsIdPropertyKey, apnsId);
            } catch (final RejectedNotificationException e) {
                context.channel().writeAndFlush(new RejectNotificationResponse(streamId, apnsId, e.getErrorReason(), e.getDeviceTokenExpirationTimestamp()));
            }
        }
    }

    protected void verifyHeaders(final Http2Headers headers) throws RejectedNotificationException {
        final String topic;
        {
            final CharSequence topicSequence = headers.get(APNS_TOPIC_HEADER);
            topic = (topicSequence != null) ? topicSequence.toString() : null;
        }

        if (topic == null) {
            throw new RejectedNotificationException(ErrorReason.MISSING_TOPIC);
        }

        {
            final Integer priorityCode = headers.getInt(APNS_PRIORITY_HEADER);

            if (priorityCode != null) {
                try {
                    DeliveryPriority.getFromCode(priorityCode);
                } catch (final IllegalArgumentException e) {
                    throw new RejectedNotificationException(ErrorReason.BAD_PRIORITY);
                }
            }
        }

        {
            final CharSequence pathSequence = headers.get(Http2Headers.PseudoHeaderName.PATH.value());

            if (pathSequence != null) {
                final String pathString = pathSequence.toString();

                if (pathString.startsWith(APNS_PATH_PREFIX)) {
                    final String tokenString = pathString.substring(APNS_PATH_PREFIX.length());

                    final Matcher tokenMatcher = DEVICE_TOKEN_PATTERN.matcher(tokenString);

                    if (!tokenMatcher.matches()) {
                        throw new RejectedNotificationException(ErrorReason.BAD_DEVICE_TOKEN);
                    }

                    final boolean deviceTokenRegisteredForTopic;
                    final Date expirationTimestamp;
                    {
                        final Map<String, Date> expirationTimestampsByDeviceToken = this.deviceTokenExpirationsByTopic.get(topic);

                        expirationTimestamp = expirationTimestampsByDeviceToken != null ? expirationTimestampsByDeviceToken.get(tokenString) : null;
                        deviceTokenRegisteredForTopic = expirationTimestampsByDeviceToken != null && expirationTimestampsByDeviceToken.containsKey(tokenString);
                    }

                    if (expirationTimestamp != null) {
                        throw new RejectedNotificationException(ErrorReason.UNREGISTERED, expirationTimestamp);
                    }

                    if (!deviceTokenRegisteredForTopic) {
                        throw new RejectedNotificationException(ErrorReason.DEVICE_TOKEN_NOT_FOR_TOPIC);
                    }
                }
            } else {
                throw new RejectedNotificationException(ErrorReason.BAD_PATH);
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

    @Override
    public void write(final ChannelHandlerContext context, final Object message, final ChannelPromise writePromise) throws Exception {
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
