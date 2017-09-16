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

import com.turo.pushy.apns.DeliveryPriority;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class ValidatingPushNotificationHandler implements PushNotificationHandler {

    private final Map<String, Set<String>> deviceTokensByTopic;
    private final Map<String, Date> expirationTimestampsByDeviceToken;

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_PRIORITY_HEADER = new AsciiString("apns-priority");
    private static final AsciiString APNS_ID_HEADER = new AsciiString("apns-id");
    private static final AsciiString APNS_COLLAPSE_ID_HEADER = new AsciiString("apns-collapse-id");

    private static final Pattern DEVICE_TOKEN_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

    private static final int MAX_PAYLOAD_SIZE = 4096;
    private static final int MAX_COLLAPSE_ID_SIZE = 64;

    ValidatingPushNotificationHandler(final Map<String, Set<String>> deviceTokensByTopic, final Map<String, Date> expirationTimestampsByDeviceToken) {
        this.deviceTokensByTopic = deviceTokensByTopic;
        this.expirationTimestampsByDeviceToken = expirationTimestampsByDeviceToken;
    }

    @Override
    public void handlePushNotification(final Http2Headers headers, final ByteBuf payload) throws RejectedNotificationException {

        final UUID apnsId;

        try {
            final CharSequence apnsIdSequence = headers.get(APNS_ID_HEADER);
            apnsId = apnsIdSequence != null ? UUID.fromString(apnsIdSequence.toString()) : UUID.randomUUID();
        } catch (final IllegalArgumentException e) {
            throw new RejectedNotificationException(RejectionReason.BAD_MESSAGE_ID, null);
        }

        if (!HttpMethod.POST.asciiName().contentEquals(headers.get(Http2Headers.PseudoHeaderName.METHOD.value()))) {
            throw new RejectedNotificationException(RejectionReason.METHOD_NOT_ALLOWED, apnsId);
        }

        final String topic;
        {
            final CharSequence topicSequence = headers.get(APNS_TOPIC_HEADER);

            if (topicSequence == null) {
                throw new RejectedNotificationException(RejectionReason.MISSING_TOPIC, apnsId);
            }

            topic = topicSequence.toString();
        }

        {
            final CharSequence collapseIdSequence = headers.get(APNS_COLLAPSE_ID_HEADER);

            if (collapseIdSequence != null && collapseIdSequence.toString().getBytes(StandardCharsets.UTF_8).length > MAX_COLLAPSE_ID_SIZE) {
                throw new RejectedNotificationException(RejectionReason.BAD_COLLAPSE_ID, apnsId);
            }
        }

        {
            final Integer priorityCode = headers.getInt(APNS_PRIORITY_HEADER);

            if (priorityCode != null) {
                try {
                    DeliveryPriority.getFromCode(priorityCode);
                } catch (final IllegalArgumentException e) {
                    throw new RejectedNotificationException(RejectionReason.BAD_PRIORITY, apnsId);
                }
            }
        }

        {
            final CharSequence pathSequence = headers.get(Http2Headers.PseudoHeaderName.PATH.value());

            if (pathSequence != null) {
                final String pathString = pathSequence.toString();

                if (pathSequence.toString().equals(APNS_PATH_PREFIX)) {
                    throw new RejectedNotificationException(RejectionReason.MISSING_DEVICE_TOKEN, apnsId);
                } else if (pathString.startsWith(APNS_PATH_PREFIX)) {
                    final String deviceToken = pathString.substring(APNS_PATH_PREFIX.length());

                    final Matcher tokenMatcher = DEVICE_TOKEN_PATTERN.matcher(deviceToken);

                    if (!tokenMatcher.matches()) {
                        throw new RejectedNotificationException(RejectionReason.BAD_DEVICE_TOKEN, apnsId);
                    }

                    final Date expirationTimestamp = this.expirationTimestampsByDeviceToken.get(deviceToken);

                    if (expirationTimestamp != null) {
                        throw new UnregisteredDeviceTokenException(expirationTimestamp, apnsId);
                    }

                    final Set<String> allowedDeviceTokensForTopic = this.deviceTokensByTopic.get(topic);

                    if (allowedDeviceTokensForTopic == null || !allowedDeviceTokensForTopic.contains(deviceToken)) {
                        throw new RejectedNotificationException(RejectionReason.DEVICE_TOKEN_NOT_FOR_TOPIC, apnsId);
                    }
                } else {
                    throw new RejectedNotificationException(RejectionReason.BAD_PATH, apnsId);
                }
            } else {
                throw new RejectedNotificationException(RejectionReason.BAD_PATH, apnsId);
            }
        }

        this.verifyAuthentication(headers, apnsId);

        if (payload == null || payload.readableBytes() == 0) {
            throw new RejectedNotificationException(RejectionReason.PAYLOAD_EMPTY, apnsId);
        }

        if (payload.readableBytes() > MAX_PAYLOAD_SIZE) {
            throw new RejectedNotificationException(RejectionReason.PAYLOAD_TOO_LARGE, apnsId);
        }
    }

    protected abstract void verifyAuthentication(final Http2Headers headers, final UUID apnsId) throws RejectedNotificationException;
}
