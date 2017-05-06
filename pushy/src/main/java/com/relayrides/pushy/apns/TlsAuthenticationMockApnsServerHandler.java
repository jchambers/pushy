package com.relayrides.pushy.apns;

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
