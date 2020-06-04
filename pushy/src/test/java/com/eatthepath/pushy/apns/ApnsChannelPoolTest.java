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

package com.eatthepath.pushy.apns;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.local.LocalChannel;
import io.netty.util.concurrent.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ApnsChannelPoolTest {

    private static class TestChannel extends LocalChannel {
        private boolean active;

        private final ChannelPromise closePromise;

        public TestChannel(final boolean initiallyActive) {
            this.active = initiallyActive;
            this.closePromise = new DefaultChannelPromise(this, EVENT_EXECUTOR);
        }

        public void setActive(final boolean active) {
            this.active = active;
        }

        @Override
        public boolean isActive() {
            return this.active;
        }

        @Override
        public ChannelFuture close() {
            this.closePromise.trySuccess();
            return this.closePromise;
        }

        @Override
        public ChannelFuture closeFuture() {
            return this.closePromise;
        }
    }

    private static class TestChannelFactory implements PooledObjectFactory<Channel> {
        @Override
        public Future<Channel> create(final Promise<Channel> promise) {
            promise.trySuccess(new TestChannel(true));
            return promise;
        }

        @Override
        public Future<Void> destroy(final Channel channel, final Promise<Void> promise) {
            channel.close().addListener(new PromiseNotifier<>(promise));
            return promise;
        }
    }

    private static class TestChannelPoolMetricListener implements ApnsChannelPoolMetricsListener {

        private final AtomicInteger connectionsAdded = new AtomicInteger(0);
        private final AtomicInteger connectionsRemoved = new AtomicInteger(0);
        private final AtomicInteger connectionsFailed = new AtomicInteger(0);

        @Override
        public void handleConnectionAdded() {
            this.connectionsAdded.incrementAndGet();
        }

        public int getConnectionsAdded() {
            return this.connectionsAdded.get();
        }

        @Override
        public void handleConnectionRemoved() {
            this.connectionsRemoved.incrementAndGet();
        }

        public int getConnectionsRemoved() {
            return this.connectionsRemoved.get();
        }

        @Override
        public void handleConnectionCreationFailed() {
            this.connectionsFailed.incrementAndGet();
        }

        public int getConnectionsFailed() {
            return this.connectionsFailed.get();
        }
    }

    private ApnsChannelPool pool;
    private TestChannelPoolMetricListener metricsListener;

    private static OrderedEventExecutor EVENT_EXECUTOR;

    @BeforeAll
    public static void setUpBeforeClass() {
        EVENT_EXECUTOR = new DefaultEventExecutor();
    }

    @BeforeEach
    public void setUp() {
        this.metricsListener = new TestChannelPoolMetricListener();
        this.pool = new ApnsChannelPool(new TestChannelFactory(), 1, EVENT_EXECUTOR, this.metricsListener);
    }

    @AfterAll
    public static void tearDownAfterClass() throws Exception {
        EVENT_EXECUTOR.shutdownGracefully().await();
    }

    @Test
    void testAcquireRelease() throws Exception {
        final Future<Channel> firstAcquireFuture = this.pool.acquire();
        final Future<Channel> secondAcquireFuture = this.pool.acquire();

        assertTrue(firstAcquireFuture.await().isSuccess());
        final Channel firstChannel = firstAcquireFuture.getNow();

        assertFalse(secondAcquireFuture.await(1, TimeUnit.SECONDS));

        this.pool.release(firstChannel);

        assertTrue(secondAcquireFuture.await().isSuccess());
        final Channel secondChannel = secondAcquireFuture.getNow();

        assertSame(firstChannel, secondChannel);

        this.pool.release(secondChannel);

        assertEquals(1, this.metricsListener.getConnectionsAdded());
        assertEquals(0, this.metricsListener.getConnectionsRemoved());
        assertEquals(0, this.metricsListener.getConnectionsFailed());
    }

    @SuppressWarnings("AnonymousInnerClassMayBeStatic")
    @Test
    void testAcquireConstructionFailure() throws Exception {
        final PooledObjectFactory<Channel> factory = new PooledObjectFactory<Channel>() {
            @Override
            public Future<Channel> create(final Promise<Channel> promise) {
                promise.tryFailure(new RuntimeException());
                return promise;
            }

            @Override
            public Future<Void> destroy(final Channel channel, final Promise<Void> promise) {
                promise.trySuccess(null);
                return promise;
            }
        };

        final ApnsChannelPool pool = new ApnsChannelPool(factory, 1, EVENT_EXECUTOR, this.metricsListener);

        assertFalse(pool.acquire().await().isSuccess());

        assertEquals(0, this.metricsListener.getConnectionsAdded());
        assertEquals(0, this.metricsListener.getConnectionsRemoved());
        assertEquals(1, this.metricsListener.getConnectionsFailed());
    }

    @Test
    void testAcquireClosedChannelFromIdlePool() throws Exception {
        final Future<Channel> firstAcquireFuture = this.pool.acquire();
        assertTrue(firstAcquireFuture.await().isSuccess());

        final Channel firstChannel = firstAcquireFuture.getNow();
        ((TestChannel) firstChannel).setActive(false);

        this.pool.release(firstChannel);

        final Future<Channel> secondAcquireFuture = this.pool.acquire();
        assertTrue(secondAcquireFuture.await().isSuccess());

        final Channel secondChannel = secondAcquireFuture.getNow();

        assertNotSame(firstChannel, secondChannel);
        assertTrue(firstChannel.closeFuture().isSuccess());
        assertFalse(secondChannel.closeFuture().isDone());

        this.pool.release(secondChannel);

        assertEquals(2, this.metricsListener.getConnectionsAdded());
        assertEquals(1, this.metricsListener.getConnectionsRemoved());
        assertEquals(0, this.metricsListener.getConnectionsFailed());
    }

    @Test
    void testAcquireFromClosedPool() throws Exception {
        this.pool.close().await();

        final Future<Channel> acquireFuture = this.pool.acquire().await();
        assertFalse(acquireFuture.isSuccess());
    }

    @Test
    void testPendingAcquisitionsDuringPoolClosure() throws Exception {
        final Future<Channel> firstFuture = this.pool.acquire().await();

        assertTrue(firstFuture.isSuccess());

        final Future<Channel> pendingFuture = this.pool.acquire();

        this.pool.close().await();

        assertTrue(pendingFuture.await(1000));
        assertFalse(pendingFuture.isSuccess());
    }

    @SuppressWarnings("AnonymousInnerClassMayBeStatic")
    @Test
    void testClosePendingCreateChannelFutureDuringPoolClosure() throws Exception {
        final List<Promise<Channel>> createPromises = new ArrayList<>();

        final PooledObjectFactory<Channel> factory = new PooledObjectFactory<Channel>() {
            @Override
            public Future<Channel> create(final Promise<Channel> promise) {
                createPromises.add(promise);
                return promise;
            }

            @Override
            public Future<Void> destroy(final Channel channel, final Promise<Void> promise) {
                promise.trySuccess(null);
                return promise;
            }
        };

        final ApnsChannelPool pool = new ApnsChannelPool(factory, 1, EVENT_EXECUTOR, this.metricsListener);

        final Future<Channel> acquireNewChannelFuture = pool.acquire();
        final Future<Channel> acquireReturnedChannelFuture = pool.acquire();

        final Future<Void> closeFuture = pool.close();

        EVENT_EXECUTOR.submit(() -> {
            final TestChannel channel = new TestChannel(true);
            createPromises.forEach(channelPromise -> channelPromise.trySuccess(channel));
        });

        closeFuture.await();

        assertTrue(acquireNewChannelFuture.await().isSuccess(),
                "Futures waiting for new connections at pool closure should succeed.");

        assertFalse(acquireReturnedChannelFuture.await().isSuccess(),
                "Futures waiting for existing connections at pool closure should fail.");
    }
}
