/*
 * Copyright (c) 2013-2018 Turo
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

import com.turo.pushy.apns.ApnsPushNotification;
import com.turo.pushy.apns.DeliveryPriority;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * <p>A parsing APNs server listener is an abstract base class that parses HTTP/2 headers and payload byte buffers from
 * a mock APNs server into {@link ApnsPushNotification} instances for easier handling.</p>
 *
 * <p>Note that the mock server's decision to accept or reject a push notification is controlled by its
 * {@link PushNotificationHandler}. As a result, notifications accepted by a mock server might be rejected by a real
 * APNs server or vice versa. Push notifications parsed by this class may have missing, incomplete, or nonsense data
 * regardless of whether they were accepted or rejected, and callers should use appropriate caution.</p>
 *
 * @see MockApnsServerBuilder#setListener(MockApnsServerListener)
 *
 * @since 0.12
 */
public abstract class ParsingMockApnsServerListenerAdapter implements MockApnsServerListener {

    private static class LenientApnsPushNotification implements ApnsPushNotification {
        private final String token;
        private final String payload;
        private final Date invalidationTime;
        private final DeliveryPriority priority;
        private final String topic;
        private final String collapseId;
        private final UUID apnsId;

        private LenientApnsPushNotification(final String token, final String topic, final String payload, final Date invalidationTime, final DeliveryPriority priority, final String collapseId, final UUID apnsId) {
            this.token = token;
            this.payload = payload;
            this.invalidationTime = invalidationTime;
            this.priority = priority;
            this.topic = topic;
            this.collapseId = collapseId;
            this.apnsId = apnsId;
        }

        @Override
        public String getToken() {
            return this.token;
        }

        @Override
        public String getPayload() {
            return this.payload;
        }

        @Override
        public Date getExpiration() {
            return this.invalidationTime;
        }

        @Override
        public DeliveryPriority getPriority() {
            return this.priority;
        }

        @Override
        public String getTopic() {
            return this.topic;
        }

        @Override
        public String getCollapseId() {
            return this.collapseId;
        }

        @Override
        public UUID getApnsId() {
            return apnsId;
        }
    }

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_PRIORITY_HEADER = new AsciiString("apns-priority");
    private static final AsciiString APNS_EXPIRATION_HEADER = new AsciiString("apns-expiration");
    private static final AsciiString APNS_COLLAPSE_ID_HEADER = new AsciiString("apns-collapse-id");
    private static final AsciiString APNS_ID_HEADER = new AsciiString("apns-id");

    private static final Logger log = LoggerFactory.getLogger(ParsingMockApnsServerListenerAdapter.class);

    /**
     * Parses a push notification accepted by a mock APNs server into an {@link ApnsPushNotification} instance for
     * further processing by
     * {@link ParsingMockApnsServerListenerAdapter#handlePushNotificationAccepted(ApnsPushNotification)}.
     *
     * @param headers the notification's HTTP/2 headers
     * @param payload the notification's payload
     */
    public void handlePushNotificationAccepted(final Http2Headers headers, final ByteBuf payload) {
        this.handlePushNotificationAccepted(parsePushNotification(headers, payload));
    }

    /**
     * Handles a parsed push notification accepted by a mock server. Note that any field of the parsed push notification
     * may be {@code null}.
     *
     * @param pushNotification the notification accepted by the server
     */
    public abstract void handlePushNotificationAccepted(final ApnsPushNotification pushNotification);

    /**
     * Parses a push notification rejected by a mock APNs server into an {@link ApnsPushNotification} instance for
     * further processing by
     * {@link ParsingMockApnsServerListenerAdapter#handlePushNotificationRejected(ApnsPushNotification, RejectionReason, Date)}.
     *
     * @param headers the notification's HTTP/2 headers
     * @param payload the notification's payload
     * @param rejectionReason the reason the push notification was rejected by the mock server
     * @param deviceTokenExpirationTimestamp the time at which the push notification's destination device token expired;
     */
    public void handlePushNotificationRejected(final Http2Headers headers, final ByteBuf payload, final RejectionReason rejectionReason, final Date deviceTokenExpirationTimestamp) {
        this.handlePushNotificationRejected(parsePushNotification(headers, payload), rejectionReason, deviceTokenExpirationTimestamp);
    }

    /**
     * Handles a parsed push notification accepted by a mock server. Note that any field of the parsed push notification
     * may be {@code null}.
     *
     * @param pushNotification the push notification rejected by the server
     * @param rejectionReason the reason the push notification was rejected by the mock server
     * @param deviceTokenExpirationTimestamp the time at which the push notification's destination device token expired;
     */
    public abstract void handlePushNotificationRejected(final ApnsPushNotification pushNotification, final RejectionReason rejectionReason, final Date deviceTokenExpirationTimestamp);

    private static ApnsPushNotification parsePushNotification(final Http2Headers headers, final ByteBuf payload) {
        final UUID apnsId;
        {
            final CharSequence apnsIdSequence = headers.get(APNS_ID_HEADER);

            UUID apnsIdFromHeaders;

            try {
                apnsIdFromHeaders = apnsIdSequence != null ? UUID.fromString(apnsIdSequence.toString()) : null;
            } catch (final IllegalArgumentException e) {
                log.error("Failed to parse `apns-id` header: {}", apnsIdSequence, e);
                apnsIdFromHeaders = null;
            }

            apnsId = apnsIdFromHeaders;
        }

        final String deviceToken;
        {
            final CharSequence pathSequence = headers.get(Http2Headers.PseudoHeaderName.PATH.value());

            if (pathSequence != null) {
                final String pathString = pathSequence.toString();

                deviceToken = pathString.startsWith(APNS_PATH_PREFIX) ? pathString.substring(APNS_PATH_PREFIX.length()) : null;
            } else {
                deviceToken = null;
            }
        }

        final String topic;
        {
            final CharSequence topicSequence = headers.get(APNS_TOPIC_HEADER);
            topic = topicSequence != null ? topicSequence.toString() : null;
        }

        final DeliveryPriority deliveryPriority;
        {
            final Integer priorityCode = headers.getInt(APNS_PRIORITY_HEADER);

            DeliveryPriority priorityFromCode;

            try {
                priorityFromCode = priorityCode != null ? DeliveryPriority.getFromCode(priorityCode) : null;
            }  catch (final IllegalArgumentException e) {
                priorityFromCode = null;
            }

            deliveryPriority = priorityFromCode;
        }

        final Date expiration;
        {
            final Integer expirationTimestamp = headers.getInt(APNS_EXPIRATION_HEADER);
            expiration = expirationTimestamp != null ? new Date(expirationTimestamp * 1000) : null;
        }

        final String collapseId;
        {
            final CharSequence collapseIdSequence = headers.get(APNS_COLLAPSE_ID_HEADER);
            collapseId = collapseIdSequence != null ? collapseIdSequence.toString() : null;
        }

        // One challenge here is that we don't actually know that a push notification is valid, even if it's
        // accepted by the push notification handler (since we might be using a handler that blindly accepts
        // everything), so we want to use a lenient push notification implementation.
        return new LenientApnsPushNotification(
                deviceToken,
                topic,
                payload != null ? payload.toString(StandardCharsets.UTF_8) : null,
                expiration,
                deliveryPriority,
                collapseId,
                apnsId);
    }
}
