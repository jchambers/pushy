package com.turo.pushy.apns;

import com.turo.pushy.apns.util.concurrent.PushNotificationFuture;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;

class PushNotificationPromise<P extends ApnsPushNotification, V> extends DefaultPromise<V> implements PushNotificationFuture<P, V> {

    private final P pushNotification;

    // save retry count
    private int retryCount;

    PushNotificationPromise(final EventExecutor eventExecutor, final P pushNotification) {
        super(eventExecutor);
        retryCount = 0;
        this.pushNotification = pushNotification;
    }

    @Override
    public P getPushNotification() {
        return this.pushNotification;
    }

    public int retryAndGet() {
        return ++retryCount;
    }
}
