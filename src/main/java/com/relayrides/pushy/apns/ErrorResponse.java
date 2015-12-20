package com.relayrides.pushy.apns;

import java.util.Date;

class ErrorResponse {
    private final String reason;
    private final Integer timestamp;

    public ErrorResponse(final String reason, final Date timestamp) {
        this(reason, timestamp != null ? (int) (timestamp.getTime() / 1000) : null);
    }

    public ErrorResponse(final String reason, final Integer timestamp) {
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public String getReason() {
        return this.reason;
    }

    public Integer getTimestamp() {
        return this.timestamp;
    }
}
