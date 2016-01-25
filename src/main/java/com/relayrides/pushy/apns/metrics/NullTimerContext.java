package com.relayrides.pushy.apns.metrics;

/**
 * A no-op timer context, used for the case when there isn't currently a running timer.
 */
public class NullTimerContext implements Timer.Context {
    @Override
    public void stop() {
        // no-op
    }
}
