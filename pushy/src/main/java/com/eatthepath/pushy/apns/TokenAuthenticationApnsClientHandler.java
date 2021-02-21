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

package com.eatthepath.pushy.apns;

import com.eatthepath.pushy.apns.auth.AuthenticationTokenProvider;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AsciiString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

class TokenAuthenticationApnsClientHandler extends ApnsClientHandler {

    private final AuthenticationTokenProvider authenticationTokenProvider;

    private static final AsciiString APNS_AUTHORIZATION_HEADER = new AsciiString("authorization");

    private static final String EXPIRED_AUTH_TOKEN_REASON = "ExpiredProviderToken";

    private static final Logger log = LoggerFactory.getLogger(TokenAuthenticationApnsClientHandler.class);

    public static class TokenAuthenticationApnsClientHandlerBuilder extends ApnsClientHandlerBuilder {
        private AuthenticationTokenProvider authenticationTokenProvider;

        public TokenAuthenticationApnsClientHandlerBuilder authenticationTokenProvider(final AuthenticationTokenProvider authenticationTokenProvider) {
            this.authenticationTokenProvider = authenticationTokenProvider;
            return this;
        }

        public AuthenticationTokenProvider authenticationTokenProvider() {
            return this.authenticationTokenProvider;
        }

        @Override
        public ApnsClientHandler build(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
            Objects.requireNonNull(this.authority(), "Authority must be set before building a TokenAuthenticationApnsClientHandler.");
            Objects.requireNonNull(this.authenticationTokenProvider(), "Authentication token provider must be set before building a TokenAuthenticationApnsClientHandler.");

            final ApnsClientHandler handler = new TokenAuthenticationApnsClientHandler(decoder, encoder, initialSettings, this.authority(), this.idlePingInterval(), this.authenticationTokenProvider());
            this.frameListener(handler);
            return handler;
        }
    }

    protected TokenAuthenticationApnsClientHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final String authority, final Duration idlePingInterval, final AuthenticationTokenProvider authenticationTokenProvider) {
        super(decoder, encoder, initialSettings, authority, idlePingInterval);

        this.authenticationTokenProvider = Objects.requireNonNull(authenticationTokenProvider, "Authentication token provider must not be null for token-based client handlers.");
    }

    @Override
    protected Http2Headers getHeadersForPushNotification(final ApnsPushNotification pushNotification, final ChannelHandlerContext context, final int streamId) {
        return super.getHeadersForPushNotification(pushNotification, context, streamId)
                .add(APNS_AUTHORIZATION_HEADER, this.authenticationTokenProvider.getAuthenticationToken().getAuthorizationHeader());
    }

    @Override
    protected void handleErrorResponse(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final ApnsPushNotification pushNotification, final ErrorResponse errorResponse) {
        super.handleErrorResponse(context, streamId, headers, pushNotification, errorResponse);

        if (EXPIRED_AUTH_TOKEN_REASON.equals(errorResponse.getReason())) {
            log.warn("APNs server reports token for channel {} has expired.", context.channel());

            // Once the server thinks our token has expired, it will "wedge" the connection. There's no way to recover
            // from this situation, and all we can do is close the connection and create a new one.
            context.close();
        }
    }
}
