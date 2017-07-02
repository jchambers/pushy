package com.turo.pushy.apns;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.local.LocalChannel;
import io.netty.util.concurrent.*;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

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

    @BeforeClass
    public static void setUpBeforeClass() {
        EVENT_EXECUTOR = new DefaultEventExecutor();
    }

    @Before
    public void setUp() {
        this.metricsListener = new TestChannelPoolMetricListener();
        this.pool = new ApnsChannelPool(new TestChannelFactory(), 1, EVENT_EXECUTOR, this.metricsListener);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        EVENT_EXECUTOR.shutdownGracefully().await();
    }

    @Test
    public void testAcquireRelease() throws Exception {
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

    @Test
    public void testAcquireConstructionFailure() throws Exception {
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
    public void testAcquireClosedChannelFromIdlePool() throws Exception {
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
}
