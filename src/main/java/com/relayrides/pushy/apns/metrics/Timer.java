package com.relayrides.pushy.apns.metrics;

/**
 * A Timer metric.
 */
public interface Timer {

    /**
     * @return a newly-started timer context. This should run until the first
     * invocation of the Context's stop() method.
     */
    Context start();

    interface Context {
        /**
         * Stop this running timer and record its value.  Later invocations of this method
         * on this object should be considered a no-op.
         */
        void stop();
    }
}
