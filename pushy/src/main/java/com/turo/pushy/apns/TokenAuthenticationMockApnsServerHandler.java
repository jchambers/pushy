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

import com.turo.pushy.apns.auth.ApnsVerificationKey;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AsciiString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Date;
import java.util.Map;
import java.util.Set;

class TokenAuthenticationMockApnsServerHandler extends AbstractMockApnsServerHandler {

    private final boolean emulateExpiredFirstToken;
    private boolean rejectedFirstExpiredToken = false;

    private final Map<String, ApnsVerificationKey> verificationKeysByKeyId;
    private final Map<ApnsVerificationKey, Set<String>> topicsByVerificationKey;

    private String expectedTeamId;

    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_AUTHORIZATION_HEADER = new AsciiString("authorization");

    private static final Logger log = LoggerFactory.getLogger(TokenAuthenticationApnsClientHandler.class);

    public static final class TokenAuthenticationMockApnsServerHandlerBuilder extends AbstractMockApnsServerHandlerBuilder {

        private boolean emulateExpiredFirstToken;

        private Map<String, ApnsVerificationKey> verificationKeysByKeyId;
        private Map<ApnsVerificationKey, Set<String>> topicsByVerificationKey;

        public AbstractMockApnsServerHandlerBuilder emulateExpiredFirstToken(final boolean emulateExpiredFirstToken) {
            this.emulateExpiredFirstToken = emulateExpiredFirstToken;
            return this;
        }

        public AbstractMockApnsServerHandlerBuilder verificationKeysByKeyId(final Map<String, ApnsVerificationKey> verificationKeysByKeyId) {
            this.verificationKeysByKeyId = verificationKeysByKeyId;
            return this;
        }

        public AbstractMockApnsServerHandlerBuilder topicsByVerificationKey(final Map<ApnsVerificationKey, Set<String>> topicsByVerificationKey) {
            this.topicsByVerificationKey = topicsByVerificationKey;
            return this;
        }

        @Override
        public TokenAuthenticationMockApnsServerHandler build(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
            final TokenAuthenticationMockApnsServerHandler handler = new TokenAuthenticationMockApnsServerHandler(decoder, encoder, initialSettings, super.emulateInternalErrors(), super.deviceTokenExpirationsByTopic(), emulateExpiredFirstToken, verificationKeysByKeyId, topicsByVerificationKey);
            this.frameListener(handler);
            return handler;
        }

        @Override
        public AbstractMockApnsServerHandler build() {
            return super.build();
        }
    }

    protected TokenAuthenticationMockApnsServerHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final boolean emulateInternalErrors, final Map<String, Map<String, Date>> deviceTokenExpirationsByTopic, final boolean emulateExpiredFirstToken, final Map<String, ApnsVerificationKey> verificationKeysByKeyId, final Map<ApnsVerificationKey, Set<String>> topicsByVerificationKey) {
        super(decoder, encoder, initialSettings, emulateInternalErrors, deviceTokenExpirationsByTopic);

        this.emulateExpiredFirstToken = emulateExpiredFirstToken;

        this.verificationKeysByKeyId = verificationKeysByKeyId;
        this.topicsByVerificationKey = topicsByVerificationKey;
    }

    @Override
    protected void verifyHeaders(final Http2Headers headers) throws RejectedNotificationException {
        super.verifyHeaders(headers);

        final String base64EncodedAuthenticationToken;
        {
            final CharSequence authorizationSequence = headers.get(APNS_AUTHORIZATION_HEADER);

            if (authorizationSequence != null) {
                final String authorizationString = authorizationSequence.toString();

                if (authorizationString.startsWith("bearer")) {
                    base64EncodedAuthenticationToken = authorizationString.substring("bearer".length()).trim();
                } else {
                    base64EncodedAuthenticationToken = null;
                }
            } else {
                base64EncodedAuthenticationToken = null;
            }
        }

        final AuthenticationToken authenticationToken = new AuthenticationToken(base64EncodedAuthenticationToken);
        final ApnsVerificationKey verificationKey = this.verificationKeysByKeyId.get(authenticationToken.getKeyId());

        // Have we ever heard of the key in question?
        if (verificationKey == null) {
            throw new RejectedNotificationException(ErrorReason.INVALID_PROVIDER_TOKEN);
        }

        try {
            if (!authenticationToken.verifySignature(verificationKey)) {
                throw new RejectedNotificationException(ErrorReason.INVALID_PROVIDER_TOKEN);
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            // This should never happen (here, at least) because we check keys at construction time. If something's
            // going to go wrong, it will go wrong before we ever get here.
            log.error("Failed to verify authentication token signature.", e);
            throw new RuntimeException(e);
        }

        // At this point, we've verified that the token is signed by somebody with the named team's private key. The
        // real APNs server only allows one team per connection, so if this is our first notification, we want to keep
        // track of the team that sent it so we can reject notifications from other teams, even if they're signed
        // correctly.
        if (this.expectedTeamId == null) {
            this.expectedTeamId = authenticationToken.getTeamId();
        }

        if (!this.expectedTeamId.equals(authenticationToken.getTeamId())) {
            throw new RejectedNotificationException(ErrorReason.INVALID_PROVIDER_TOKEN);
        }

        if (authenticationToken.getIssuedAt().getTime() + MockApnsServer.AUTHENTICATION_TOKEN_EXPIRATION_MILLIS < System.currentTimeMillis()) {
            throw new RejectedNotificationException(ErrorReason.EXPIRED_PROVIDER_TOKEN);
        }

        if (this.emulateExpiredFirstToken && !this.rejectedFirstExpiredToken) {
            this.rejectedFirstExpiredToken = true;
            throw new RejectedNotificationException(ErrorReason.EXPIRED_PROVIDER_TOKEN);
        }

        final String topic;
        {
            final CharSequence topicSequence = headers.get(APNS_TOPIC_HEADER);
            topic = (topicSequence != null) ? topicSequence.toString() : null;
        }

        final Set<String> topicsAllowedForVerificationKey = this.topicsByVerificationKey.get(verificationKey);

        if (topicsAllowedForVerificationKey == null || !topicsAllowedForVerificationKey.contains(topic)) {
            throw new RejectedNotificationException(ErrorReason.INVALID_PROVIDER_TOKEN);
        }
    }
}
