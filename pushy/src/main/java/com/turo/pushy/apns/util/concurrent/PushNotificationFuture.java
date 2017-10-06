package com.turo.pushy.apns.util.concurrent;

import com.turo.pushy.apns.ApnsPushNotification;
import io.netty.util.concurrent.Future;

/**
 * A push notification future represents the result an operation on a push notification.
 *
 * @param <P> the type of push notification sent
 * @param <V> the type of value returned by the operation
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public interface PushNotificationFuture<P extends ApnsPushNotification, V> extends Future<V> {

    /**
     * Returns the push notification to which the operation represented by this future applies.
     *
     * @return the push notification to which the operation represented by this future applies
     */
    P getPushNotification();
}
