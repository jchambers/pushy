package com.relayrides.pushy.apns.metrics;

/**
 * Interface which metrics implementations need to implement.
 *
 * This is pretty simplistic; improvements could include enum keys instead of strings for the metric
 * names, or pre-declared metric objects encapsulating the state for a Counter, a Timer, etc.
 *
 * I would envisage a set of additional Maven modules for "pushy-dropwizard-metrics-publisher",
 * "pushy-statsd-metrics-publisher" etc., which provide the necessary dependency declarations,
 * and a factory to create PushyMetrics objects suitable to publish to whatever metrics system
 * is in use.  (Regarding https://github.com/relayrides/pushy/issues/175#issuecomment-172857510 ,
 * this would be the ideal place to specify the string prefix for the ApnsClient's metrics set.)
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
