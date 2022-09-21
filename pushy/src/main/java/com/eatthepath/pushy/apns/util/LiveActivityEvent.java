package com.eatthepath.pushy.apns.util;

public enum LiveActivityEvent {
    UPDATE("update"),

    END("end");

    private final String value;

    LiveActivityEvent(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
