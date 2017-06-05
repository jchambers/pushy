package com.relayrides.pushy.apns;

public interface HandlerMetrics {

    void recordMaxConcurrentStreams(long maxConcurrentStreams);

    void maxStreamsHit();

    void pingSent();

    void pingWriteFailure();

    void pingTimeout();

    void pingSuccess();

    void goAway();

}
