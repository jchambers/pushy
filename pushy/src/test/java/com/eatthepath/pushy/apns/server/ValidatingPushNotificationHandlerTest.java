/*
 * Copyright (c) 2020 Jon Chambers
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

package com.eatthepath.pushy.apns.server;

import com.eatthepath.pushy.apns.DeliveryPriority;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public abstract class ValidatingPushNotificationHandlerTest {

    protected static final String TOKEN = "1234567890123456789012345678901234567890123456789012345678901234";
    protected static final String TOPIC = "topic";

    private static final String APNS_PATH_PREFIX = "/3/device/";

    private static final AsciiString APNS_EXPIRATION_HEADER = new AsciiString("apns-expiration");
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_PRIORITY_HEADER = new AsciiString("apns-priority");
    private static final AsciiString APNS_COLLAPSE_ID_HEADER = new AsciiString("apns-collapse-id");
    private static final AsciiString APNS_ID_HEADER = new AsciiString("apns-id");

    protected static final Map<String, Set<String>> DEVICE_TOKENS_BY_TOPIC =
            Collections.singletonMap(TOPIC, Collections.singleton(TOKEN));

    protected Http2Headers headers;
    protected ByteBuf payload;

    @SuppressWarnings("SameParameterValue")
    protected abstract ValidatingPushNotificationHandler getHandler(final Map<String, Set<String>> deviceTokensByTopic, final Map<String, Instant> expirationTimestampsByDeviceToken);

    protected abstract void addAcceptableCredentialsToHeaders(Http2Headers headers) throws Exception;

    @BeforeEach
    public void setUp() throws Exception {
        this.headers = new DefaultHttp2Headers()
                .method(HttpMethod.POST.asciiName())
                .authority(getClass().getName())
                .path(APNS_PATH_PREFIX + TOKEN);

        this.headers.add(APNS_TOPIC_HEADER, TOPIC);

        this.addAcceptableCredentialsToHeaders(this.headers);

        this.payload = UnpooledByteBufAllocator.DEFAULT.buffer();
        this.payload.writeBytes("{}".getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    public void tearDown() {
        this.payload.release();
    }

    @Test
    void testHandleNotificationWithValidNotification() throws Exception {
        this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap())
                .handlePushNotification(this.headers, this.payload);
    }

    @Test
    void testHandleNotificationWithBadApnsId() {
        this.headers.set(APNS_ID_HEADER, "This is not a valid UUID.");

        this.assertNotificationRejected("Push notifications with an invalid APNs ID should be rejected.",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap()),
                this.headers,
                this.payload,
                RejectionReason.BAD_MESSAGE_ID);
    }

    @Test
    void testHandleNotificationWithCollapseId() throws Exception {
        this.headers.set(APNS_COLLAPSE_ID_HEADER, "Collapse ID!");

        this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap())
                .handlePushNotification(this.headers, this.payload);
    }

    @Test
    void testHandleNotificationWithOversizedCollapseId() {
        this.headers.set(APNS_COLLAPSE_ID_HEADER, "1234567890123456789012345678901234567890123456789012345678901234567890");

        this.assertNotificationRejected("Push notifications with a collapse ID longer than 64 bytes should be rejected.",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap()),
                this.headers,
                this.payload,
                RejectionReason.BAD_COLLAPSE_ID);
    }

    @Test
    void testHandleNotificationWithExpirationDate() throws Exception {
        this.headers.setInt(APNS_EXPIRATION_HEADER, (int) Instant.now().getEpochSecond());

        this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap())
                .handlePushNotification(this.headers, this.payload);
    }

    @Test
    void testHandleNotificationWithMissingTopic() {
        this.headers.remove(APNS_TOPIC_HEADER);

        this.assertNotificationRejected("Push notifications without a topic should be rejected.",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap()),
                this.headers,
                this.payload,
                RejectionReason.MISSING_TOPIC);
    }

    @Test
    void testHandleNotificationWithSpecifiedPriority() throws Exception {
        this.headers.setInt(APNS_PRIORITY_HEADER, DeliveryPriority.CONSERVE_POWER.getCode());

        this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap())
                .handlePushNotification(this.headers, this.payload);

        this.headers.setInt(APNS_PRIORITY_HEADER, DeliveryPriority.IMMEDIATE.getCode());

        this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap())
                .handlePushNotification(this.headers, this.payload);
    }

    @Test
    void testHandleNotificationWithBadPriority() {
        this.headers.setInt(APNS_PRIORITY_HEADER, 9000);

        this.assertNotificationRejected("Push notifications without an unrecognized priority should be rejected.",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap()),
                this.headers,
                this.payload,
                RejectionReason.BAD_PRIORITY);
    }

    @Test
    void testHandleNotificationWithBadPath() {
        this.headers.path("/example/definitely-not-the-correct-path/");

        this.assertNotificationRejected("Push notifications with a bad path should be rejected.",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap()),
                this.headers,
                this.payload,
                RejectionReason.BAD_PATH);
    }

    @Test
    void testHandleNotificationWithMissingDeviceToken() {
        this.headers.path(APNS_PATH_PREFIX);

        this.assertNotificationRejected("Push notifications with no device token should be rejected.",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap()),
                this.headers,
                this.payload,
                RejectionReason.MISSING_DEVICE_TOKEN);
    }

    @Test
    void testHandleNotificationWithBadDeviceToken() {
        this.headers.path(APNS_PATH_PREFIX + "Definitely not a legit device token.");

        this.assertNotificationRejected("Push notifications with a malformed device token should be rejected.",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap()),
                this.headers,
                this.payload,
                RejectionReason.BAD_DEVICE_TOKEN);
    }

    @Test
    void testHandleNotificationWithExpiredDeviceToken() {
        final Map<String, Instant> deviceTokenExpirationDates =
                Collections.singletonMap(TOKEN, Instant.now().minusMillis(1));

        this.assertNotificationRejected("Push notifications with an expired device token should be rejected.",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, deviceTokenExpirationDates),
                this.headers,
                this.payload,
                RejectionReason.UNREGISTERED);
    }

    @Test
    void testHandleNotificationWithDeviceTokenForWrongTopic() {
        this.headers.set(APNS_TOPIC_HEADER, TOPIC + ".definitely.wrong");

        this.assertNotificationRejected("Push notifications with a device token for the wrong topic should be rejected.",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap()),
                this.headers,
                this.payload,
                RejectionReason.DEVICE_TOKEN_NOT_FOR_TOPIC);
    }

    @Test
    void testHandleNotificationWithMissingPayload() {
        this.assertNotificationRejected("Push notifications with a device token for the wrong topic should be rejected.",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap()),
                this.headers,
                null,
                RejectionReason.PAYLOAD_EMPTY);
    }

    @Test
    void testHandleNotificationWithEmptyPayload() {
        final ByteBuf emptyPayload = UnpooledByteBufAllocator.DEFAULT.buffer();

        try {
            this.assertNotificationRejected("Push notifications with a device token for the wrong topic should be rejected.",
                    this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap()),
                    this.headers,
                    emptyPayload,
                    RejectionReason.PAYLOAD_EMPTY);
        } finally {
            emptyPayload.release();
        }
    }

    @Test
    void testHandleNotificationWithOversizedPayload() {
        final int size = 4096 * 2;
        final ByteBuf largePayload = UnpooledByteBufAllocator.DEFAULT.buffer(size);

        try {
            final byte[] payloadBytes = new byte[size];
            new Random().nextBytes(payloadBytes);

            largePayload.writeBytes(payloadBytes);

            this.assertNotificationRejected("Push notifications with a device token for the wrong topic should be rejected.",
                    this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.emptyMap()),
                    this.headers,
                    largePayload,
                    RejectionReason.PAYLOAD_TOO_LARGE);
        } finally {
            largePayload.release();
        }
    }

    protected void assertNotificationRejected(final String message, final ValidatingPushNotificationHandler handler, final Http2Headers headers, final ByteBuf payload, final RejectionReason expectedRejectionReason) {
        final RejectedNotificationException rejectedNotificationException =
                assertThrows(RejectedNotificationException.class, () -> handler.handlePushNotification(headers, payload), message);

        assertEquals(expectedRejectionReason, rejectedNotificationException.getRejectionReason(), message);
    }
}
