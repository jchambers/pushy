package com.relayrides.pushy.apns;

public interface HandlerMetrics {

    void recordSubmitToWriteLag(long millis);

    void recordMaxConcurrentStreams(long maxConcurrentStreams);

    void maxStreamsHit();

    void pingSent();

    void pingWriteFailure();

    void pingTimeout();

    void pingSuccess();

    void goAway();

}
