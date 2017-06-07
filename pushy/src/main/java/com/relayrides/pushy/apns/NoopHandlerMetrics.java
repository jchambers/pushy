package com.relayrides.pushy.apns;

public final class NoopHandlerMetrics implements HandlerMetrics {

    @Override
    public void recordSubmitToWriteLag(long millis) {

    }

    @Override
    public void recordMaxConcurrentStreams(long maxConcurrentStreams) {

    }

    @Override
    public void maxStreamsHit() {

    }

    @Override
    public void pingSent() {

    }

    @Override
    public void pingWriteFailure() {

    }

    @Override
    public void pingTimeout() {

    }

    @Override
    public void pingSuccess() {

    }

    @Override
    public void goAway() {

    }
}
