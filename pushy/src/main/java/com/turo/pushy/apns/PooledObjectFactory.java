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

package com.turo.pushy.apns;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

/**
 * Constructs new objects for use in an asynchronous object pool.
 *
 * @param <T> the type of object created by this factory
 */
interface PooledObjectFactory <T> {

    /**
     * Asynchronously creates a new object for use in an asynchronous object pool.
     *
     * @param promise a {@code Promise} to be notified when a new pooled object has been created
     *
     * @return a {@code Future} that will complete when a new pooled object has been created
     */
    Future<T> create(Promise<T> promise);

    /**
     * Asynchronously destroys an object evicted from an asynchronous object pool.
     *
     * @param object the object to destroy
     * @param promise a {@code Promise} to be notified when the object has been destroyed
     *
     * @return a {@code Future} that will complete when the given object has been destroyed
     */
    Future<Void> destroy(T object, Promise<Void> promise);
}
