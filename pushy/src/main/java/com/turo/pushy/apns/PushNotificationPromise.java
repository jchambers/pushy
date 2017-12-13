package com.turo.pushy.apns;

import com.turo.pushy.apns.util.concurrent.PushNotificationFuture;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;

class PushNotificationPromise<P extends ApnsPushNotification, V> extends DefaultPromise<V> implements PushNotificationFuture<P, V> {

    private final P pushNotification;

    PushNotificationPromise(final EventExecutor eventExecutor, final P pushNotification) {
        super(eventExecutor);

        this.pushNotification = pushNotification;
    }

    @Override
    public P getPushNotification() {
        return this.pushNotification;
    }
}
