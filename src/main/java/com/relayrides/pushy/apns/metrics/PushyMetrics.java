package com.relayrides.pushy.apns.metrics;

/**
 * Interface which metrics implementations need to implement.
 *
 * This is pretty simplistic; improvements could include enum keys instead of strings for the metric
 * names, or pre-declared metric objects encapsulating the state for a Counter, a Timer, etc.
 */
public interface PushyMetrics {

    /**
     * Increment a counter metric named @param name.
     */
    Counter counter(String name);

    /**
     * @return a newly-started timer, named @param name. This should run until the first
     * invocation of the TimerContext's stop() method.
     */
    Timer timer(String name);
}
