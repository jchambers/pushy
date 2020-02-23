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

import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.proxy.ProxyHandlerFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider;
import io.netty.resolver.dns.RoundRobinDnsAddressResolverGroup;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An APNs channel factory creates new channels connected to an APNs server. Channels constructed by this factory are
 * intended for use in an {@link ApnsChannelPool}.
 */
class ApnsChannelFactory implements PooledObjectFactory<Channel>, Closeable {

    private final SslContext sslContext;
    private final AtomicBoolean hasReleasedSslContext = new AtomicBoolean(false);

    private final AddressResolverGroup<? extends SocketAddress> addressResolverGroup;

    private final Bootstrap bootstrapTemplate;

    private final AtomicLong currentDelaySeconds = new AtomicLong(0);

    private static final long MIN_CONNECT_DELAY_SECONDS = 1;
    private static final long MAX_CONNECT_DELAY_SECONDS = 60;

    private static final AttributeKey<Promise<Channel>> CHANNEL_READY_PROMISE_ATTRIBUTE_KEY =
            AttributeKey.valueOf(ApnsChannelFactory.class, "channelReadyPromise");

    private static final Logger log = LoggerFactory.getLogger(ApnsChannelFactory.class);

    ApnsChannelFactory(final SslContext sslContext, final ApnsSigningKey signingKey, final long tokenExpirationMillis,
                       final ProxyHandlerFactory proxyHandlerFactory, final int connectTimeoutMillis,
                       final long idlePingIntervalMillis, final long gracefulShutdownTimeoutMillis,
                       final Http2FrameLogger frameLogger, final InetSocketAddress apnsServerAddress,
                       final EventLoopGroup eventLoopGroup) {

        this.sslContext = sslContext;

        if (this.sslContext instanceof ReferenceCounted) {
            ((ReferenceCounted) this.sslContext).retain();
        }

        this.addressResolverGroup = proxyHandlerFactory == null ?
                new RoundRobinDnsAddressResolverGroup(ClientChannelClassUtil.getDatagramChannelClass(eventLoopGroup),
                        DefaultDnsServerAddressStreamProvider.INSTANCE) : NoopAddressResolverGroup.INSTANCE;

        this.bootstrapTemplate = new Bootstrap();
        this.bootstrapTemplate.group(eventLoopGroup);
        this.bootstrapTemplate.option(ChannelOption.TCP_NODELAY, true);
        this.bootstrapTemplate.remoteAddress(apnsServerAddress);
        this.bootstrapTemplate.resolver(this.addressResolverGroup);

        if (connectTimeoutMillis > 0) {
            this.bootstrapTemplate.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis);
        }

        this.bootstrapTemplate.handler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel) {
                final SslHandler sslHandler = sslContext.newHandler(channel.alloc());

                sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<Channel>>() {
                    @Override
                    public void operationComplete(final Future<Channel> handshakeFuture) {
                        if (handshakeFuture.isSuccess()) {
                            channel.attr(CHANNEL_READY_PROMISE_ATTRIBUTE_KEY).get().trySuccess(channel);
                        } else {
                            tryFailureAndLogRejectedCause(channel.attr(CHANNEL_READY_PROMISE_ATTRIBUTE_KEY).get(), handshakeFuture.cause());
                        }
                    }
                });

                final ApnsClientHandler apnsClientHandler;
                {
                    final String authority = apnsServerAddress.getHostName();

                    final ApnsClientHandler.ApnsClientHandlerBuilder clientHandlerBuilder;

                    if (signingKey != null) {
                        clientHandlerBuilder = new TokenAuthenticationApnsClientHandler.TokenAuthenticationApnsClientHandlerBuilder()
                                .signingKey(signingKey)
                                .tokenExpirationMillis(tokenExpirationMillis)
                                .authority(authority)
                                .idlePingIntervalMillis(idlePingIntervalMillis);
                    } else {
                        clientHandlerBuilder = new ApnsClientHandler.ApnsClientHandlerBuilder()
                                .authority(authority)
                                .idlePingIntervalMillis(idlePingIntervalMillis);
                    }

                    if (frameLogger != null) {
                        clientHandlerBuilder.frameLogger(frameLogger);
                    }

                    apnsClientHandler = clientHandlerBuilder.build();

                    if (gracefulShutdownTimeoutMillis > 0) {
                        apnsClientHandler.gracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis);
                    }
                }

                final ChannelPipeline pipeline = channel.pipeline();

                if (proxyHandlerFactory != null) {
                    pipeline.addFirst(proxyHandlerFactory.createProxyHandler());
                }

                pipeline.addLast(sslHandler);
                pipeline.addLast(new FlushConsolidationHandler(FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES, true));
                pipeline.addLast(new IdleStateHandler(idlePingIntervalMillis, 0, 0, TimeUnit.MILLISECONDS));
                pipeline.addLast(apnsClientHandler);
            }
        });
    }

    /**
     * Creates and connects a new channel. The initial connection attempt may be delayed to accommodate exponential
     * back-off requirements.
     *
     * @param channelReadyPromise the promise to be notified when a channel has been created and connected to the APNs
     * server
     *
     * @return a future that will be notified once a channel has been created and connected to the APNs server
     */
    @Override
    public Future<Channel> create(final Promise<Channel> channelReadyPromise) {
        final long delay = this.currentDelaySeconds.get();

        channelReadyPromise.addListener(new GenericFutureListener<Future<Channel>>() {

            @Override
            public void operationComplete(final Future<Channel> future) {
                final long updatedDelay = future.isSuccess() ? 0 :
                        Math.max(Math.min(delay * 2, MAX_CONNECT_DELAY_SECONDS), MIN_CONNECT_DELAY_SECONDS);

                ApnsChannelFactory.this.currentDelaySeconds.compareAndSet(delay, updatedDelay);
            }
        });


        this.bootstrapTemplate.config().group().schedule(new Runnable() {

            @Override
            public void run() {

                final Bootstrap bootstrap = ApnsChannelFactory.this.bootstrapTemplate.clone()
                        .channelFactory(new AugmentingReflectiveChannelFactory<>(
                                ClientChannelClassUtil.getSocketChannelClass(ApnsChannelFactory.this.bootstrapTemplate.config().group()),
                                CHANNEL_READY_PROMISE_ATTRIBUTE_KEY, channelReadyPromise));

                final ChannelFuture connectFuture = bootstrap.connect();

                connectFuture.addListener(new GenericFutureListener<ChannelFuture>() {

                    @Override
                    public void operationComplete(final ChannelFuture future) {
                        if (!future.isSuccess()) {
                            // This may seem spurious, but our goal here is to accurately report the cause of
                            // connection failure; if we just wait for connection closure, we won't be able to
                            // tell callers anything more specific about what went wrong.
                            tryFailureAndLogRejectedCause(channelReadyPromise, future.cause());
                        }
                    }
                });

                connectFuture.channel().closeFuture().addListener(new GenericFutureListener<ChannelFuture> () {

                    @Override
                    public void operationComplete(final ChannelFuture future) {
                        // We always want to try to fail the "channel ready" promise if the connection closes; if it has
                        // already succeeded, this will have no effect.
                        channelReadyPromise.tryFailure(
                                new IllegalStateException("Channel closed before HTTP/2 preface completed."));
                    }
                });

            }
        }, delay, TimeUnit.SECONDS);

        return channelReadyPromise;
    }

    /**
     * Destroys a channel by closing it.
     *
     * @param channel the channel to destroy
     * @param promise the promise to notify when the channel has been destroyed
     *
     * @return a future that will be notified when the channel has been destroyed
     */
    @Override
    public Future<Void> destroy(final Channel channel, final Promise<Void> promise) {
        channel.close().addListener(new PromiseNotifier<>(promise));
        return promise;
    }

    @Override
    public void close() {
        this.addressResolverGroup.close();

        if (this.sslContext instanceof ReferenceCounted) {
            if (this.hasReleasedSslContext.compareAndSet(false, true)) {
                ((ReferenceCounted) this.sslContext).release();
            }
        }
    }

    private static void tryFailureAndLogRejectedCause(final Promise<?> promise, final Throwable cause) {
        if (!promise.tryFailure(cause)) {
            log.warn("Tried to mark promise as \"failed,\" but it was already done.", cause);
        }
    }
}
