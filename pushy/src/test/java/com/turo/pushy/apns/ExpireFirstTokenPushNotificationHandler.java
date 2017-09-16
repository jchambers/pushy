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

import com.turo.pushy.apns.server.PushNotificationHandler;
import com.turo.pushy.apns.server.RejectedNotificationException;
import com.turo.pushy.apns.server.RejectionReason;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;

class ExpireFirstTokenPushNotificationHandler implements PushNotificationHandler {

    private String rejectedAuthorizationHeader;

    private static final AsciiString APNS_AUTHORIZATION_HEADER = new AsciiString("authorization");

    @Override
    public void handlePushNotification(final Http2Headers headers, final ByteBuf payload) throws RejectedNotificationException {
        final CharSequence authorizationSequence = headers.get(APNS_AUTHORIZATION_HEADER);

        if (authorizationSequence != null) {
            final String authorizationHeader = authorizationSequence.toString();

            if (this.rejectedAuthorizationHeader == null) {
                this.rejectedAuthorizationHeader = authorizationHeader;
            }

            if (this.rejectedAuthorizationHeader.equals(authorizationHeader)) {
                throw new RejectedNotificationException(RejectionReason.EXPIRED_PROVIDER_TOKEN, null);
            }
        }
    }
}
