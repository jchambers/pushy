/*
 * Copyright (c) 2020 Jon Chambers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.eatthepath.pushy.apns.util.concurrent;

import com.eatthepath.pushy.apns.ApnsPushNotification;

import java.util.concurrent.CompletableFuture;

/**
 * A push notification future represents the result an asynchronous operation on a {@link ApnsPushNotification}.
 *
 * @param <P> the type of push notification sent
 * @param <V> the type of value returned by the operation
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public class PushNotificationFuture<P extends ApnsPushNotification, V> extends CompletableFuture<V> {

    private final P pushNotification;

    public PushNotificationFuture(final P pushNotification) {
        super();

        this.pushNotification = pushNotification;
    }

    /**
     * Returns the push notification to which the operation represented by this future applies.
     *
     * @return the push notification to which the operation represented by this future applies
     */
    public P getPushNotification() {
        return this.pushNotification;
    }
}
