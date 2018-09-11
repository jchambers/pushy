/*
 * Copyright (c) 2013-2017 Turo
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

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * <p>A pool of channels connected to an APNs server. Channel pools use a {@link ApnsChannelFactory} to create
 * connections (up to a given maximum capacity) on demand.</p>
 *
 * <p>Callers acquire channels from the pool via the {@link ApnsChannelPool#acquire()} method, and must return them to
 * the pool with the {@link ApnsChannelPool#release(Channel)} method. When channels are acquired, they are unavailable
 * to other callers until they are released back into the pool.</p>
 *
 * <p>Channel pools are intended to be long-lived, persistent resources. When an application no longer needs a channel
 * pool (presumably because it is shutting down), it must shut down the channel pool via the
 * {@link ApnsChannelPool#close()} method.</p>
 *
 * @since 0.11
 */
class ApnsChannelPool {

    private final PooledObjectFactory<Channel> channelFactory;
    private final OrderedEventExecutor executor;
    private final int capacity;

    private final ApnsChannelPoolMetricsListener metricsListener;

    private final ChannelGroup allChannels;
    private final Queue<Channel> idleChannels = new ArrayDeque<>();

    private final Set<Future<Channel>> pendingCreateChannelFutures = new HashSet<>();
    private final Queue<Promise<Channel>> pendingAcquisitionPromises = new ArrayDeque<>();

    private boolean isClosed = false;

    private static final Exception POOL_CLOSED_EXCEPTION =
            new IllegalStateException("Channel pool has closed and no more channels may be acquired.");

    private static final Logger log = LoggerFactory.getLogger(ApnsChannelPool.class);

    private static class NoopChannelPoolMetricsListener implements ApnsChannelPoolMetricsListener {

        @Override
        public void handleConnectionAdded() {
        }

        @Override
        public void handleConnectionRemoved() {
        }

        @Override
        public void handleConnectionCreationFailed() {
        }
    }

    /**
     * Constructs a new channel pool that will create new channels with the given {@code channelFactory} and has the
     * given maximum channel {@code capacity}.
     *
     * @param channelFactory the factory to be used to create new channels
     * @param capacity the maximum number of channels that may be held in this pool
     * @param executor the executor on which listeners for acquisition/release promises will be called
     * @param metricsListener an optional listener for metrics describing the performance and behavior of the pool
     */
    ApnsChannelPool(final PooledObjectFactory<Channel> channelFactory, final int capacity, final OrderedEventExecutor executor, final ApnsChannelPoolMetricsListener metricsListener) {
        this.channelFactory = channelFactory;
        this.capacity = capacity;
        this.executor = executor;

        this.metricsListener = metricsListener != null ? metricsListener : new NoopChannelPoolMetricsListener();

        this.allChannels = new DefaultChannelGroup(this.executor, true);
    }

    /**
     * <p>Asynchronously acquires a channel from this channel pool. The acquired channel may be a pre-existing channel
     * stored in the pool or may be a new channel created on demand. If no channels are available and the pool is at
     * capacity, acquisition may be delayed until another caller releases a channel to the pool.</p>
     *
     * <p>When callers are done with a channel, they <em>must</em> release the channel back to the pool via the
     * {@link ApnsChannelPool#release(Channel)} method.</p>
     *
     * @return a {@code Future} that will be notified when a channel is available
     *
     * @see ApnsChannelPool#release(Channel)
     */
    Future<Channel> acquire() {
        final Promise<Channel> acquirePromise = new DefaultPromise<>(this.executor);

        if (this.executor.inEventLoop()) {
            this.acquireWithinEventExecutor(acquirePromise);
        } else {
            this.executor.submit(new Runnable() {
                @Override
                public void run() {
                    ApnsChannelPool.this.acquireWithinEventExecutor(acquirePromise);
                }
            }).addListener(new GenericFutureListener() {
                @Override
                public void operationComplete(final Future future) throws Exception {
                    if (!future.isSuccess()) {
                        acquirePromise.tryFailure(future.cause());
                    }
                }
            });
        }

        return acquirePromise;
    }

    private void acquireWithinEventExecutor(final Promise<Channel> acquirePromise) {
        assert this.executor.inEventLoop();

        if (!this.isClosed) {
            // We always want to open new channels if we have spare capacity. Once the pool is full, we'll start looking
            // for idle, pre-existing channels.
            if (this.allChannels.size() + this.pendingCreateChannelFutures.size() < this.capacity) {
                final Future<Channel> createChannelFuture = this.channelFactory.create(executor.<Channel>newPromise());
                this.pendingCreateChannelFutures.add(createChannelFuture);

                createChannelFuture.addListener(new GenericFutureListener<Future<Channel>>() {

                    @Override
                    public void operationComplete(final Future<Channel> future) {
                        ApnsChannelPool.this.pendingCreateChannelFutures.remove(createChannelFuture);

                        if (future.isSuccess()) {
                            final Channel channel = future.getNow();

                            ApnsChannelPool.this.allChannels.add(channel);
                            ApnsChannelPool.this.metricsListener.handleConnectionAdded();

                            acquirePromise.trySuccess(channel);
                        } else {
                            ApnsChannelPool.this.metricsListener.handleConnectionCreationFailed();

                            acquirePromise.tryFailure(future.cause());

                            // If we failed to open a connection, this is the end of the line for this acquisition
                            // attempt, and callers won't be able to release the channel (since they didn't get one
                            // in the first place). Move on to the next acquisition attempt if one is present.
                            ApnsChannelPool.this.handleNextAcquisition();
                        }
                    }
                });
            } else {
                final Channel channelFromIdlePool = ApnsChannelPool.this.idleChannels.poll();

                if (channelFromIdlePool != null) {
                    if (channelFromIdlePool.isActive()) {
                        acquirePromise.trySuccess(channelFromIdlePool);
                    } else {
                        // The channel from the idle pool isn't usable; discard it and create a new one instead
                        this.discardChannel(channelFromIdlePool);
                        this.acquireWithinEventExecutor(acquirePromise);
                    }
                } else {
                    // We don't have any connections ready to go, and don't have any more capacity to create new
                    // channels. Add this acquisition to the queue waiting for channels to become available.
                    pendingAcquisitionPromises.add(acquirePromise);
                }
            }
        } else {
            acquirePromise.tryFailure(POOL_CLOSED_EXCEPTION);
        }
    }

    /**
     * Returns a previously-acquired channel to the pool.
     *
     * @param channel the channel to return to the pool
     */
    void release(final Channel channel) {
        if (this.executor.inEventLoop()) {
            this.releaseWithinEventExecutor(channel);
        } else {
            this.executor.submit(new Runnable() {
                @Override
                public void run() {
                    ApnsChannelPool.this.releaseWithinEventExecutor(channel);
                }
            });
        }
    }

    private void releaseWithinEventExecutor(final Channel channel) {
        assert this.executor.inEventLoop();

        this.idleChannels.add(channel);
        this.handleNextAcquisition();
    }

    private void handleNextAcquisition() {
        assert this.executor.inEventLoop();

        if (!this.pendingAcquisitionPromises.isEmpty()) {
            this.acquireWithinEventExecutor(this.pendingAcquisitionPromises.poll());
        }
    }

    private void discardChannel(final Channel channel) {
        assert this.executor.inEventLoop();

        this.idleChannels.remove(channel);
        this.allChannels.remove(channel);

        this.metricsListener.handleConnectionRemoved();

        this.channelFactory.destroy(channel, this.executor.<Void>newPromise()).addListener(new GenericFutureListener<Future<Void>>() {
            @Override
            public void operationComplete(final Future<Void> destroyFuture) throws Exception {
                if (!destroyFuture.isSuccess()) {
                    log.warn("Failed to destroy channel.", destroyFuture.cause());
                }
            }
        });
    }

    /**
     * Shuts down this channel pool and releases all retained resources.
     *
     * @return a {@code Future} that will be completed when all resources held by this pool have been released
     */
    public Future<Void> close() {
        return this.allChannels.close().addListener(new GenericFutureListener<Future<Void>>() {
            @Override
            public void operationComplete(final Future<Void> future) throws Exception {
                ApnsChannelPool.this.isClosed = true;

                if (ApnsChannelPool.this.channelFactory instanceof Closeable) {
                    ((Closeable) ApnsChannelPool.this.channelFactory).close();
                }

                for (final Promise<Channel> acquisitionPromise : ApnsChannelPool.this.pendingAcquisitionPromises) {
                    acquisitionPromise.tryFailure(POOL_CLOSED_EXCEPTION);
                }
            }
        });
    }
}
