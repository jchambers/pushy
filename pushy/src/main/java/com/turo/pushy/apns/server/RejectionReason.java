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

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * An enumeration of reasons a push notification may be rejected by an APNs server. The most up-to-date descriptions of
 * each rejection reason are available in Table 8-6 of Apple's <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/CommunicatingwithAPNs.html#//apple_ref/doc/uid/TP40008194-CH11-SW1">Local
 * and Remote Notification Programming Guide - Communicating with APNs</a>.
 */
public enum RejectionReason {
    BAD_COLLAPSE_ID("BadCollapseId", HttpResponseStatus.BAD_REQUEST),
    BAD_DEVICE_TOKEN("BadDeviceToken", HttpResponseStatus.BAD_REQUEST),
    BAD_EXPIRATION_DATE("BadExpirationDate", HttpResponseStatus.BAD_REQUEST),
    BAD_MESSAGE_ID("BadMessageId", HttpResponseStatus.BAD_REQUEST),
    BAD_PRIORITY("BadPriority", HttpResponseStatus.BAD_REQUEST),
    BAD_TOPIC("BadTopic", HttpResponseStatus.BAD_REQUEST),
    DEVICE_TOKEN_NOT_FOR_TOPIC("DeviceTokenNotForTopic", HttpResponseStatus.BAD_REQUEST),
    DUPLICATE_HEADERS("DuplicateHeaders", HttpResponseStatus.BAD_REQUEST),
    IDLE_TIMEOUT("IdleTimeout", HttpResponseStatus.BAD_REQUEST),
    MISSING_DEVICE_TOKEN("MissingDeviceToken", HttpResponseStatus.BAD_REQUEST),
    MISSING_TOPIC("MissingTopic", HttpResponseStatus.BAD_REQUEST),
    PAYLOAD_EMPTY("PayloadEmpty", HttpResponseStatus.BAD_REQUEST),
    TOPIC_DISALLOWED("TopicDisallowed", HttpResponseStatus.BAD_REQUEST),

    BAD_CERTIFICATE("BadCertificate", HttpResponseStatus.FORBIDDEN),
    BAD_CERTIFICATE_ENVIRONMENT("BadCertificateEnvironment", HttpResponseStatus.FORBIDDEN),
    EXPIRED_PROVIDER_TOKEN("ExpiredProviderToken", HttpResponseStatus.FORBIDDEN),
    FORBIDDEN("Forbidden", HttpResponseStatus.FORBIDDEN),
    INVALID_PROVIDER_TOKEN("InvalidProviderToken", HttpResponseStatus.FORBIDDEN),
    MISSING_PROVIDER_TOKEN("MissingProviderToken", HttpResponseStatus.FORBIDDEN),

    BAD_PATH("BadPath", HttpResponseStatus.NOT_FOUND),

    METHOD_NOT_ALLOWED("MethodNotAllowed", HttpResponseStatus.METHOD_NOT_ALLOWED),

    UNREGISTERED("Unregistered", HttpResponseStatus.GONE),

    PAYLOAD_TOO_LARGE("PayloadTooLarge", HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE),

    TOO_MANY_PROVIDER_TOKEN_UPDATES("TooManyProviderTokenUpdates", HttpResponseStatus.TOO_MANY_REQUESTS),
    TOO_MANY_REQUESTS("TooManyRequests", HttpResponseStatus.TOO_MANY_REQUESTS),

    INTERNAL_SERVER_ERROR("InternalServerError", HttpResponseStatus.INTERNAL_SERVER_ERROR),

    SERVICE_UNAVAILABLE("ServiceUnavailable", HttpResponseStatus.SERVICE_UNAVAILABLE),
    SHUTDOWN("Shutdown", HttpResponseStatus.SERVICE_UNAVAILABLE);

    private final String reasonText;
    private final HttpResponseStatus httpResponseStatus;

    RejectionReason(final String reasonText, final HttpResponseStatus httpResponseStatus) {
        this.reasonText = reasonText;
        this.httpResponseStatus = httpResponseStatus;
    }

    String getReasonText() {
        return this.reasonText;
    }

    HttpResponseStatus getHttpResponseStatus() {
        return this.httpResponseStatus;
    }
}
