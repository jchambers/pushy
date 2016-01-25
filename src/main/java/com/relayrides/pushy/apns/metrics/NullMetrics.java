package com.relayrides.pushy.apns.metrics;

/**
 * A null metrics sink implementation.
 */
public class NullMetrics implements PushyMetrics {
    private static final Timer.Context nullTimerContext = new NullTimerContext();
    private static final Counter nullCounter = new NullCounter();
    private static final Timer nullTimer = new NullTimer();

    @Override
    public Counter counter(String name) {
        return nullCounter;
    }

    @Override
    public Timer timer(String name) {
        return nullTimer;
    }

    private static class NullCounter implements Counter {
        @Override
        public void inc() {
            // no-op
        }
    }

    private static class NullTimer implements Timer {
        @Override
        public Context start() {
            return nullTimerContext;
        }
    }
}
