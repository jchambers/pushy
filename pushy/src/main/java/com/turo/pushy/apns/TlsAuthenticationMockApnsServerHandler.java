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

import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AsciiString;

import java.util.*;

class TlsAuthenticationMockApnsServerHandler extends AbstractMockApnsServerHandler {

    private final Set<String> allowedTopics;

    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");

    public static final class TlsAuthenticationMockApnsServerHandlerBuilder extends AbstractMockApnsServerHandlerBuilder {

        private String baseTopic;

        public AbstractMockApnsServerHandlerBuilder baseTopic(final String baseTopic) {
            this.baseTopic = baseTopic;
            return this;
        }

        @Override
        public TlsAuthenticationMockApnsServerHandler build(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
            final TlsAuthenticationMockApnsServerHandler handler = new TlsAuthenticationMockApnsServerHandler(decoder, encoder, initialSettings, super.emulateInternalErrors(), super.deviceTokenExpirationsByTopic(), baseTopic);
            this.frameListener(handler);
            return handler;
        }

        @Override
        public AbstractMockApnsServerHandler build() {
            return super.build();
        }
    }

    protected TlsAuthenticationMockApnsServerHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final boolean emulateInternalErrors, final Map<String, Map<String, Date>> deviceTokenExpirationsByTopic, final String baseTopic) {
        super(decoder, encoder, initialSettings, emulateInternalErrors, deviceTokenExpirationsByTopic);

        Objects.requireNonNull(baseTopic, "Base topic must not be null for mock server handlers using TLS-based authentication.");

        this.allowedTopics = new HashSet<>();
        this.allowedTopics.add(baseTopic);
        this.allowedTopics.add(baseTopic + ".voip");
        this.allowedTopics.add(baseTopic + ".complication");
    }

    @Override
    protected void verifyHeaders(final Http2Headers headers) throws RejectedNotificationException {
        super.verifyHeaders(headers);

        final String topic;
        {
            final CharSequence topicSequence = headers.get(APNS_TOPIC_HEADER);
            topic = (topicSequence != null) ? topicSequence.toString() : null;
        }

        if (!this.allowedTopics.contains(topic)) {
            throw new RejectedNotificationException(ErrorReason.BAD_TOPIC);
        }
    }
}
