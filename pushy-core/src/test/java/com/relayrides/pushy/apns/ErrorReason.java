package com.relayrides.pushy.apns;

import io.netty.handler.codec.http.HttpResponseStatus;

public enum ErrorReason {
    PAYLOAD_EMPTY("PayloadEmpty", HttpResponseStatus.BAD_REQUEST),
    PAYLOAD_TOO_LARGE("PayloadTooLarge", HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE),
    BAD_TOPIC("BadTopic", HttpResponseStatus.BAD_REQUEST),
    TOPIC_DISALLOWED("TopicDisallowed", HttpResponseStatus.FORBIDDEN),
    BAD_MESSAGE_ID("BadMessageId", HttpResponseStatus.BAD_REQUEST),
    BAD_EXPIRATION_DATE("BadExpirationDate", HttpResponseStatus.BAD_REQUEST),
    BAD_PRIORITY("BadPriority", HttpResponseStatus.BAD_REQUEST),
    MISSING_DEVICE_TOKEN("MissingDeviceToken", HttpResponseStatus.BAD_REQUEST),
    BAD_DEVICE_TOKEN("BadDeviceToken", HttpResponseStatus.BAD_REQUEST),
    DEVICE_TOKEN_NOT_FOR_TOPIC("DeviceTokenNotForTopic", HttpResponseStatus.BAD_REQUEST),
    UNREGISTERED("Unregistered", HttpResponseStatus.GONE),
    DUPLICATE_HEADERS("DuplicateHeaders", HttpResponseStatus.BAD_REQUEST),
    BAD_CERTIFICATE_ENVIRONMENT("BadCertificateEnvironment", HttpResponseStatus.FORBIDDEN),
    BAD_CERTIFICATE("BadCertificate", HttpResponseStatus.FORBIDDEN),
    FORBIDDEN("Forbidden", HttpResponseStatus.FORBIDDEN),
    BAD_PATH("BadPath", HttpResponseStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("MethodNotAllowed", HttpResponseStatus.METHOD_NOT_ALLOWED),
    TOO_MANY_REQUESTS("TooManyRequests", HttpResponseStatus.TOO_MANY_REQUESTS),
    IDLE_TIMEOUT("IdleTimeout", HttpResponseStatus.BAD_REQUEST),
    SHUTDOWN("Shutdown", HttpResponseStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("InternalServerError", HttpResponseStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE("ServiceUnavailable", HttpResponseStatus.SERVICE_UNAVAILABLE),
    MISSING_TOPIC("MissingTopic", HttpResponseStatus.BAD_REQUEST);

    private final String reasonText;
    private final HttpResponseStatus httpResponseStatus;

    private ErrorReason(final String reasonText, final HttpResponseStatus httpResponseStatus) {
        this.reasonText = reasonText;
        this.httpResponseStatus = httpResponseStatus;
    }

    public String getReasonText() {
        return reasonText;
    }

    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }
}
