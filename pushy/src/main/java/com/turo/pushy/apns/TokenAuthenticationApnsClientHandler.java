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

import com.turo.pushy.apns.auth.ApnsSigningKey;
import com.turo.pushy.apns.auth.AuthenticationToken;
import io.netty.channel.ChannelHandlerContext;
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
import java.util.Objects;

class TokenAuthenticationApnsClientHandler extends ApnsClientHandler {

    private final ApnsSigningKey signingKey;

    private AuthenticationToken authenticationToken;
    private int mostRecentStreamWithNewToken = 0;

    private static final AsciiString APNS_AUTHORIZATION_HEADER = new AsciiString("authorization");

    private static final String EXPIRED_AUTH_TOKEN_REASON = "ExpiredProviderToken";

    private static final Logger log = LoggerFactory.getLogger(TokenAuthenticationApnsClientHandler.class);

    public static class TokenAuthenticationApnsClientHandlerBuilder extends ApnsClientHandlerBuilder {
        private ApnsSigningKey signingKey;

        public TokenAuthenticationApnsClientHandlerBuilder signingKey(final ApnsSigningKey signingKey) {
            this.signingKey = signingKey;
            return this;
        }

        public ApnsSigningKey signingKey() {
            return this.signingKey;
        }

        @Override
        public ApnsClientHandler build(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
            Objects.requireNonNull(this.authority(), "Authority must be set before building a TokenAuthenticationApnsClientHandler.");
            Objects.requireNonNull(this.signingKey(), "Signing key must be set before building a TokenAuthenticationApnsClientHandler.");

            final ApnsClientHandler handler = new TokenAuthenticationApnsClientHandler(decoder, encoder, initialSettings, this.authority(), this.signingKey(), this.idlePingIntervalMillis());
            this.frameListener(handler);
            return handler;
        }
    }

    protected TokenAuthenticationApnsClientHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final String authority, final ApnsSigningKey signingKey, final long idlePingIntervalMillis) {
        super(decoder, encoder, initialSettings, authority, idlePingIntervalMillis);

        Objects.requireNonNull(signingKey, "Signing key must not be null for token-based client handlers.");
        this.signingKey = signingKey;
    }

    @Override
    protected Http2Headers getHeadersForPushNotification(final ApnsPushNotification pushNotification, final int streamId) {
        final Http2Headers headers = super.getHeadersForPushNotification(pushNotification, streamId);

        if (this.authenticationToken == null) {
            try {
                this.authenticationToken = new AuthenticationToken(signingKey, new Date());
                this.mostRecentStreamWithNewToken = streamId;
            } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
                // This should never happen because we check the key/algorithm at signing key construction time.
                log.error("Failed to generate authentication token.", e);
                throw new RuntimeException(e);
            }
        }

        headers.add(APNS_AUTHORIZATION_HEADER, this.authenticationToken.getAuthorizationHeader());

        return headers;
    }

    @Override
    protected void handleErrorResponse(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final ApnsPushNotification pushNotification, final ErrorResponse errorResponse) {
        if (TokenAuthenticationApnsClientHandler.EXPIRED_AUTH_TOKEN_REASON.equals(errorResponse.getReason())) {
            if (streamId >= this.mostRecentStreamWithNewToken) {
                this.authenticationToken = null;
            }

            // Once we've invalidated an expired token, it's reasonable to expect that re-sending the notification might
            // succeed.
            this.retryPushNotificationFromStream(context, streamId);
        } else {
            super.handleErrorResponse(context, streamId, headers, pushNotification, errorResponse);
        }
    }
}
