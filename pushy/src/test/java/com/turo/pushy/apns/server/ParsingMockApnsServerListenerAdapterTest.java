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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ParsingMockApnsServerListenerAdapterTest {

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_PRIORITY_HEADER = new AsciiString("apns-priority");
    private static final AsciiString APNS_EXPIRATION_HEADER = new AsciiString("apns-expiration");
    private static final AsciiString APNS_COLLAPSE_ID_HEADER = new AsciiString("apns-collapse-id");

    private static class TestParsingMockApnsServerListener extends ParsingMockApnsServerListenerAdapter {

        private ApnsPushNotification mostRecentPushNotification;
        private RejectionReason mostRecentRejectionReason;
        private Date mostRecentDeviceTokenExpiration;

        @Override
        public void handlePushNotificationAccepted(final ApnsPushNotification pushNotification) {
            this.mostRecentPushNotification = pushNotification;
            this.mostRecentRejectionReason = null;
            this.mostRecentDeviceTokenExpiration = null;
        }

        @Override
        public void handlePushNotificationRejected(final ApnsPushNotification pushNotification, final RejectionReason rejectionReason, final Date deviceTokenExpirationTimestamp) {
            this.mostRecentPushNotification = pushNotification;
            this.mostRecentRejectionReason = rejectionReason;
            this.mostRecentDeviceTokenExpiration = deviceTokenExpirationTimestamp;
        }
    }

    @Test
    public void testHandlePushNotificationAccepted() {
        final TestParsingMockApnsServerListener listener = new TestParsingMockApnsServerListener();

        {
            final String token = "test-token";

            final Http2Headers headers = new DefaultHttp2Headers().path(APNS_PATH_PREFIX + token);

            listener.handlePushNotificationAccepted(headers, null);

            assertEquals(token, listener.mostRecentPushNotification.getToken());
            assertNull(listener.mostRecentPushNotification.getTopic());
            assertNull(listener.mostRecentPushNotification.getPriority());
            assertNull(listener.mostRecentPushNotification.getExpiration());
            assertNull(listener.mostRecentPushNotification.getCollapseId());
            assertNull(listener.mostRecentPushNotification.getPayload());
        }

        {
            final String topic = "test-topic";

            final Http2Headers headers = new DefaultHttp2Headers().set(APNS_TOPIC_HEADER, topic);

            listener.handlePushNotificationAccepted(headers, null);

            assertNull(listener.mostRecentPushNotification.getToken());
            assertEquals(topic, listener.mostRecentPushNotification.getTopic());
            assertNull(listener.mostRecentPushNotification.getPriority());
            assertNull(listener.mostRecentPushNotification.getExpiration());
            assertNull(listener.mostRecentPushNotification.getCollapseId());
            assertNull(listener.mostRecentPushNotification.getPayload());
        }

        {
            final DeliveryPriority priority = DeliveryPriority.CONSERVE_POWER;

            final Http2Headers headers = new DefaultHttp2Headers().addInt(APNS_PRIORITY_HEADER, priority.getCode());

            listener.handlePushNotificationAccepted(headers, null);

            assertNull(listener.mostRecentPushNotification.getToken());
            assertNull(listener.mostRecentPushNotification.getTopic());
            assertEquals(priority, listener.mostRecentPushNotification.getPriority());
            assertNull(listener.mostRecentPushNotification.getExpiration());
            assertNull(listener.mostRecentPushNotification.getCollapseId());
            assertNull(listener.mostRecentPushNotification.getPayload());
        }

        {
            final Date expiration = new Date(1_000_000_000);

            final Http2Headers headers = new DefaultHttp2Headers().addInt(APNS_EXPIRATION_HEADER, (int) (expiration.getTime() / 1000));

            listener.handlePushNotificationAccepted(headers, null);

            assertNull(listener.mostRecentPushNotification.getToken());
            assertNull(listener.mostRecentPushNotification.getTopic());
            assertNull(listener.mostRecentPushNotification.getPriority());
            assertEquals(expiration, listener.mostRecentPushNotification.getExpiration());
            assertNull(listener.mostRecentPushNotification.getCollapseId());
            assertNull(listener.mostRecentPushNotification.getPayload());
        }

        {
            final String collapseId = "collapse-id";

            final Http2Headers headers = new DefaultHttp2Headers().set(APNS_COLLAPSE_ID_HEADER, collapseId);

            listener.handlePushNotificationAccepted(headers, null);

            assertNull(listener.mostRecentPushNotification.getToken());
            assertNull(listener.mostRecentPushNotification.getTopic());
            assertNull(listener.mostRecentPushNotification.getPriority());
            assertNull(listener.mostRecentPushNotification.getExpiration());
            assertEquals(collapseId, listener.mostRecentPushNotification.getCollapseId());
            assertNull(listener.mostRecentPushNotification.getPayload());
        }

        {
            final String payload = "A test payload!";

            final Http2Headers headers = new DefaultHttp2Headers();

            final ByteBuf payloadBuffer = ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT, payload);

            try {
                listener.handlePushNotificationAccepted(headers, payloadBuffer);

                assertNull(listener.mostRecentPushNotification.getToken());
                assertNull(listener.mostRecentPushNotification.getTopic());
                assertNull(listener.mostRecentPushNotification.getPriority());
                assertNull(listener.mostRecentPushNotification.getExpiration());
                assertNull(listener.mostRecentPushNotification.getCollapseId());
                assertEquals(payload, listener.mostRecentPushNotification.getPayload());
            } finally {
                payloadBuffer.release();
            }
        }
    }

    @Test
    public void testHandlePushNotificationRejected() {
        final TestParsingMockApnsServerListener listener = new TestParsingMockApnsServerListener();

        {
            final RejectionReason rejectionReason = RejectionReason.BAD_DEVICE_TOKEN;

            listener.handlePushNotificationRejected(new DefaultHttp2Headers(), null, rejectionReason, null);
            assertEquals(rejectionReason, listener.mostRecentRejectionReason);
            assertNull(listener.mostRecentDeviceTokenExpiration);
        }

        {
            final RejectionReason rejectionReason = RejectionReason.BAD_PRIORITY;
            final Date deviceTokenExpiration = new Date();

            listener.handlePushNotificationRejected(new DefaultHttp2Headers(), null, rejectionReason, deviceTokenExpiration);
            assertEquals(rejectionReason, listener.mostRecentRejectionReason);
            assertEquals(deviceTokenExpiration, listener.mostRecentDeviceTokenExpiration);
        }
    }
}