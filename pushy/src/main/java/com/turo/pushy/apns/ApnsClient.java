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

import com.turo.pushy.apns.auth.ApnsSigningKey;
import com.turo.pushy.apns.proxy.ProxyHandlerFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>An APNs client sends push notifications to the APNs gateway. Clients authenticate themselves to APNs servers in
 * one of two ways: they may either present a TLS certificate to the server at connection time, or they may present
 * authentication tokens for each notification they send. Clients that opt to use TLS-based authentication may send
 * notifications to any topic named in the client certificate. Clients that opt to use token-based authentication may
 * send notifications to any topic associated with the team to which the client's signing key belongs. Please see the
 * <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/APNSOverview.html">Local
 * and Remote Notification Programming Guide</a> for a detailed discussion of the APNs protocol, topics, and
 * certificate/key provisioning.</p>
 *
 * <p>Clients are constructed using an {@link ApnsClientBuilder}. Callers may
 * optionally specify an {@link EventLoopGroup} when constructing a new client. If no event loop group is specified,
 * clients will create and manage their own single-thread event loop group. If many clients are operating in parallel,
 * specifying a shared event loop group serves as a mechanism to keep the total number of threads in check. Callers may
 * also want to provide a specific event loop group to take advantage of platform-specific features (i.e.
 * {@code epoll} or {@code KQueue}).</p>
 *
 * <p>Callers must either provide an SSL context with the client's certificate or a signing key at client construction
 * time. If a signing key is provided, the client will use token authentication when sending notifications; otherwise,
 * it will use TLS-based authentication. It is an error to provide both a client certificate and a signing key.</p>
 *
 * <p>Clients maintain their own internal connection pools and open connections to the APNs server on demand. As a
 * result, clients do <em>not</em> need to be "started" explicitly, and are ready to begin sending notifications as soon
 * as they're constructed.</p>
 *
 * <p>Notifications sent by a client to an APNs server are sent asynchronously. A
 * {@link io.netty.util.concurrent.Future io.netty.util.concurrent.Future} is returned immediately when a notification
 * is sent, but will not complete until the attempt to send the notification has failed, the notification has been
 * accepted by the APNs server, or the notification has been rejected by the APNs server. Please note that the
 * {@code Future} returned is a {@code io.netty.util.concurrent.Future}, which is an extension of the
 * {@link java.util.concurrent.Future java.util.concurrent.Future} interface that allows callers to attach listeners
 * that will be notified when the {@code Future} completes.</p>
 *
 * <p>APNs clients are intended to be long-lived, persistent resources. Callers must shut them down when they are no
 * longer needed (i.e. when shutting down the entire application). If an event loop group was specified at construction
 * time, callers should shut down that event loop group when all clients using that group have been disconnected.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.5
 */
public class ApnsClient {
    private final EventLoopGroup eventLoopGroup;
    private final boolean shouldShutDownEventLoopGroup;

    private final ApnsChannelPool channelPool;

    private final ApnsClientMetricsListener metricsListener;
    private final AtomicLong nextNotificationId = new AtomicLong(0);

    private volatile boolean isClosed = false;

    private static final IllegalStateException CLIENT_CLOSED_EXCEPTION =
            new IllegalStateException("Client has been closed and can no longer send push notifications.");

    private static final Logger log = LoggerFactory.getLogger(ApnsClient.class);

    private static class NoopApnsClientMetricsListener implements ApnsClientMetricsListener {

        @Override
        public void handleWriteFailure(final ApnsClient apnsClient, final long notificationId) {
        }

        @Override
        public void handleNotificationSent(final ApnsClient apnsClient, final long notificationId) {
        }

        @Override
        public void handleNotificationAccepted(final ApnsClient apnsClient, final long notificationId) {
        }

        @Override
        public void handleNotificationRejected(final ApnsClient apnsClient, final long notificationId) {
        }

        @Override
        public void handleConnectionAdded(final ApnsClient apnsClient) {
        }

        @Override
        public void handleConnectionRemoved(final ApnsClient apnsClient) {
        }

        @Override
        public void handleConnectionCreationFailed(final ApnsClient apnsClient) {
        }
    }

    protected ApnsClient(final InetSocketAddress apnsServerAddress, final SslContext sslContext,
                         final ApnsSigningKey signingKey,  final ProxyHandlerFactory proxyHandlerFactory,
                         final int connectTimeoutMillis, final long idlePingIntervalMillis,
                         final long gracefulShutdownTimeoutMillis, final int concurrentConnections,
                         final ApnsClientMetricsListener metricsListener, final EventLoopGroup eventLoopGroup) {

        if (eventLoopGroup != null) {
            this.eventLoopGroup = eventLoopGroup;
            this.shouldShutDownEventLoopGroup = false;
        } else {
            this.eventLoopGroup = new NioEventLoopGroup(1);
            this.shouldShutDownEventLoopGroup = true;
        }

        this.metricsListener = metricsListener != null ? metricsListener : new NoopApnsClientMetricsListener();

        final ApnsChannelFactory channelFactory = new ApnsChannelFactory(sslContext, signingKey, proxyHandlerFactory, connectTimeoutMillis, idlePingIntervalMillis, gracefulShutdownTimeoutMillis, apnsServerAddress, this.eventLoopGroup);

        final ApnsChannelPoolMetricsListener channelPoolMetricsListener = new ApnsChannelPoolMetricsListener() {

            @Override
            public void handleConnectionAdded() {
                ApnsClient.this.metricsListener.handleConnectionAdded(ApnsClient.this);
            }

            @Override
            public void handleConnectionRemoved() {
                ApnsClient.this.metricsListener.handleConnectionRemoved(ApnsClient.this);
            }

            @Override
            public void handleConnectionCreationFailed() {
                ApnsClient.this.metricsListener.handleConnectionCreationFailed(ApnsClient.this);
            }
        };

        this.channelPool = new ApnsChannelPool(channelFactory, concurrentConnections, this.eventLoopGroup.next(), channelPoolMetricsListener);
    }

    /**
     * <p>Sends a push notification to the APNs gateway.</p>
     *
     * <p>This method returns a {@code Future} that indicates whether the notification was accepted or rejected by the
     * gateway. If the notification was accepted, it may be delivered to its destination device at some time in the
     * future, but final delivery is not guaranteed. Rejections should be considered permanent failures, and callers
     * should <em>not</em> attempt to re-send the notification.</p>
     *
     * <p>The returned {@code Future} may fail with an exception if the notification could not be sent. Failures to
     * <em>send</em> a notification to the gateway—i.e. those that fail with exceptions—should generally be considered
     * non-permanent, and callers should attempt to re-send the notification when the underlying problem has been
     * resolved.</p>
     ** @param notification the notification to send to the APNs gateway
     *
     * @param <T> the type of notification to be sent
     *
     * @return a {@code Future} that will complete when the notification has been either accepted or rejected by the
     * APNs gateway
     *
     * @since 0.8
     */
    @SuppressWarnings("unchecked")
    public <T extends ApnsPushNotification> Future<PushNotificationResponse<T>> sendNotification(final T notification) {
        final Future<PushNotificationResponse<T>> responseFuture;

        if (!this.isClosed) {
            final Promise<PushNotificationResponse<ApnsPushNotification>> responsePromise = new DefaultPromise<>(this.eventLoopGroup.next());
            final long notificationId = this.nextNotificationId.getAndIncrement();

            this.channelPool.acquire().addListener(new GenericFutureListener<Future<Channel>>() {
                @Override
                public void operationComplete(final Future<Channel> acquireFuture) throws Exception {
                    if (acquireFuture.isSuccess()) {
                        final Channel channel = acquireFuture.getNow();

                        channel.writeAndFlush(new PushNotificationAndResponsePromise(notification, responsePromise)).addListener(new GenericFutureListener<ChannelFuture>() {

                            @Override
                            public void operationComplete(final ChannelFuture future) throws Exception {
                                if (future.isSuccess()) {
                                    ApnsClient.this.metricsListener.handleNotificationSent(ApnsClient.this, notificationId);
                                } else {
                                    ApnsClient.this.metricsListener.handleWriteFailure(ApnsClient.this, notificationId);
                                    responsePromise.tryFailure(future.cause());
                                }
                            }
                        });

                        ApnsClient.this.channelPool.release(channel);
                    } else {
                        responsePromise.tryFailure(acquireFuture.cause());
                    }
                }
            });

            responsePromise.addListener(new GenericFutureListener<Future<PushNotificationResponse<ApnsPushNotification>>>() {

                @Override
                public void operationComplete(final Future<PushNotificationResponse<ApnsPushNotification>> future) throws Exception {
                    if (future.isSuccess()) {
                        final PushNotificationResponse<ApnsPushNotification> response = future.getNow();

                        if (response.isAccepted()) {
                            ApnsClient.this.metricsListener.handleNotificationAccepted(ApnsClient.this, notificationId);
                        } else {
                            ApnsClient.this.metricsListener.handleNotificationRejected(ApnsClient.this, notificationId);
                        }
                    } else {
                        ApnsClient.this.metricsListener.handleWriteFailure(ApnsClient.this, notificationId);
                    }
                }
            });

            responseFuture = (Future) responsePromise;
        } else {
            responseFuture = new FailedFuture<>(GlobalEventExecutor.INSTANCE, CLIENT_CLOSED_EXCEPTION);
        }

        return responseFuture;
    }

    /**
     * <p>Gracefully disconnects from the APNs gateway. The disconnection process will wait until notifications that
     * have been sent to the APNs server have been either accepted or rejected. Note that some notifications passed to
     * {@link ApnsClient#sendNotification(ApnsPushNotification)} may still be enqueued and
     * not yet sent by the time the shutdown process begins; the {@code Futures} associated with those notifications
     * will fail.</p>
     *
     * <p>The returned {@code Future} will be marked as complete when the connection has closed completely. If the
     * connection is already closed when this method is called, the returned {@code Future} will be marked as complete
     * immediately.</p>
     *
     * <p>If a non-null {@code EventLoopGroup} was provided at construction time, clients may be reconnected and reused
     * after they have been disconnected. If no event loop group was provided at construction time, clients may not be
     * restarted after they have been disconnected via this method.</p>
     *
     * @return a {@code Future} that will be marked as complete when the connection has been closed
     *
     * @since 0.5
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Future<Void> close() {
        log.info("Disconnecting.");

        this.isClosed = true;

        // Since we're (maybe) going to clobber the main event loop group, we should have this promise use the global
        // event executor to notify listeners.
        final Promise<Void> disconnectPromise = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);

        this.channelPool.close().addListener(new GenericFutureListener<Future<Void>>() {

            @Override
            public void operationComplete(final Future<Void> closePoolFuture) throws Exception {
                if (ApnsClient.this.shouldShutDownEventLoopGroup) {
                    ApnsClient.this.eventLoopGroup.shutdownGracefully().addListener(new GenericFutureListener() {

                        @Override
                        public void operationComplete(final Future future) throws Exception {
                            disconnectPromise.trySuccess(null);
                        }
                    });
                } else {
                    disconnectPromise.trySuccess(null);
                }
            }
        });

        return disconnectPromise;
    }
}
