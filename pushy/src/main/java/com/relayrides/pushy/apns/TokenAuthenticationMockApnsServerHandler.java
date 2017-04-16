package com.relayrides.pushy.apns;

import com.relayrides.pushy.apns.auth.ApnsVerificationKey;
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

/**
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public class TokenAuthenticationMockApnsServerHandler extends AbstractMockApnsServerHandler {

    private final Map<String, ApnsVerificationKey> verificationKeysByKeyId;
    private final Map<ApnsVerificationKey, Set<String>> topicsByVerificationKey;

    private String expectedTeamId;

    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_AUTHORIZATION_HEADER = new AsciiString("authorization");

    private static final Logger log = LoggerFactory.getLogger(TokenAuthenticationApnsClientHandler.class);

    public static final class TokenAuthenticationMockApnsServerHandlerBuilder extends AbstractMockApnsServerHandlerBuilder {

        private Map<String, ApnsVerificationKey> verificationKeysByKeyId;
        private Map<ApnsVerificationKey, Set<String>> topicsByVerificationKey;

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
            final TokenAuthenticationMockApnsServerHandler handler = new TokenAuthenticationMockApnsServerHandler(decoder, encoder, initialSettings, super.emulateInternalErrors(), super.deviceTokenExpirationsByTopic(), verificationKeysByKeyId, topicsByVerificationKey);
            this.frameListener(handler);
            return handler;
        }

        @Override
        public AbstractMockApnsServerHandler build() {
            return super.build();
        }
    }

    protected TokenAuthenticationMockApnsServerHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final boolean emulateInternalErrors, final Map<String, Map<String, Date>> deviceTokenExpirationsByTopic, final Map<String, ApnsVerificationKey> verificationKeysByKeyId, final Map<ApnsVerificationKey, Set<String>> topicsByVerificationKey) {
        super(decoder, encoder, initialSettings, emulateInternalErrors, deviceTokenExpirationsByTopic);

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
