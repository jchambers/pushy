package com.relayrides.pushy.apns.metrics;

/**
 * A Counter metric.
 */
public interface Counter {
    /**
     * Increment the counter.
     */
    void inc();
}
