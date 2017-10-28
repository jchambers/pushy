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

import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;

import java.util.*;

class TlsAuthenticationValidatingPushNotificationHandler extends ValidatingPushNotificationHandler {

    private final Set<String> allowedTopics;

    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");

    TlsAuthenticationValidatingPushNotificationHandler(final Map<String, Set<String>> deviceTokensByTopic, final Map<String, Date> expirationTimestampsByDeviceToken, final String baseTopic) {
        super(deviceTokensByTopic, expirationTimestampsByDeviceToken);

        Objects.requireNonNull(baseTopic, "Base topic must not be null for mock server handlers using TLS-based authentication.");

        this.allowedTopics = new HashSet<>();
        this.allowedTopics.add(baseTopic);
        this.allowedTopics.add(baseTopic + ".voip");
        this.allowedTopics.add(baseTopic + ".complication");
    }

    @Override
    protected void verifyAuthentication(final Http2Headers headers, final UUID apnsId) throws RejectedNotificationException {
        final String topic;
        {
            final CharSequence topicSequence = headers.get(APNS_TOPIC_HEADER);
            topic = (topicSequence != null) ? topicSequence.toString() : null;
        }

        if (!this.allowedTopics.contains(topic)) {
            throw new RejectedNotificationException(RejectionReason.BAD_TOPIC, apnsId);
        }
    }
}
