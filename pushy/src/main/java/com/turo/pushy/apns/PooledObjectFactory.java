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
