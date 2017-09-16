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

package com.turo.pushy.apns.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.*;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>A mock APNs server is an HTTP/2 server that can be configured to respond to APNs push notifications with a variety
 * of behaviors. Mock servers are primarily useful for integration tests and benchmarks; users do <strong>not</strong>
 * need to interact with mock servers as part of normal client operation.</p>
 *
 * <p>Callers construct mock APNs servers with the {@link MockApnsServerBuilder} class, and provide a
 * {@link PushNotificationHandlerFactory} at construction time. The factory constructs {@link PushNotificationHandler}
 * instances that control how the server responds to push notifications. Pushy comes with a
 * {@link ValidatingPushNotificationHandlerFactory} that constructs handlers that emulate the behavior of a real APNs
 * server (but do not actually deliver push notifications to destination devices) and an
 * {@link AcceptAllPushNotificationHandlerFactory} that constructs handlers that unconditionally accept push
 * notifications.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.8
 */
public class MockApnsServer {

    private final SslContext sslContext;
    private final AtomicBoolean hasReleasedSslContext = new AtomicBoolean(false);

    private final ServerBootstrap bootstrap;
    private final boolean shouldShutDownEventLoopGroup;

    private ChannelGroup allChannels;

    MockApnsServer(final SslContext sslContext, final PushNotificationHandlerFactory handlerFactory,
                   final int maxConcurrentStreams, final EventLoopGroup eventLoopGroup) {
        this.sslContext = sslContext;

        if (this.sslContext instanceof ReferenceCounted) {
            ((ReferenceCounted) this.sslContext).retain();
        }

        this.bootstrap = new ServerBootstrap();

        if (eventLoopGroup != null) {
            this.bootstrap.group(eventLoopGroup);
            this.shouldShutDownEventLoopGroup = false;
        } else {
            this.bootstrap.group(new NioEventLoopGroup(1));
            this.shouldShutDownEventLoopGroup = true;
        }

        this.bootstrap.channel(ServerSocketChannelClassUtil.getServerSocketChannelClass(this.bootstrap.config().group()));
        this.bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel) throws Exception {
                final SslHandler sslHandler = sslContext.newHandler(channel.alloc());
                channel.pipeline().addLast(sslHandler);
                channel.pipeline().addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {

                    @Override
                    protected void configurePipeline(final ChannelHandlerContext context, final String protocol) throws Exception {
                        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                            final PushNotificationHandler pushNotificationHandler =
                                    handlerFactory.buildHandler(sslHandler.engine().getSession());

                            final MockApnsServerHandler serverHandler = new MockApnsServerHandler.MockApnsServerHandlerBuilder()
                                    .pushNotificationHandler(pushNotificationHandler)
                                    .initialSettings(Http2Settings.defaultSettings().maxConcurrentStreams(maxConcurrentStreams))
                                    .build();

                            context.pipeline().addLast(serverHandler);

                            MockApnsServer.this.allChannels.add(context.channel());
                        } else {
                            throw new IllegalStateException("Unexpected protocol: " + protocol);
                        }
                    }
                });
            }
        });
    }

    /**
     * Starts this mock server and listens for traffic on the given port.
     *
     * @param port the port to which this server should bind
     *
     * @return a {@code Future} that will succeed when the server has bound to the given port and is ready to accept
     * traffic
     */
    public Future<Void> start(final int port) {
        final ChannelFuture channelFuture = this.bootstrap.bind(port);

        this.allChannels = new DefaultChannelGroup(channelFuture.channel().eventLoop(), true);
        this.allChannels.add(channelFuture.channel());

        return channelFuture;
    }

    /**
     * <p>Shuts down this server and releases the port to which this server was bound. If a {@code null} event loop
     * group was provided at construction time, the server will also shut down its internally-managed event loop
     * group.</p>
     *
     * <p>If a non-null {@code EventLoopGroup} was provided at construction time, mock servers may be reconnected and
     * reused after they have been shut down. If no event loop group was provided at construction time, mock servers may
     * not be restarted after they have been shut down via this method.</p>
     *
     * @return a {@code Future} that will succeed once the server has finished unbinding from its port and, if the
     * server was managing its own event loop group, its event loop group has shut down
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Future<Void> shutdown() {
        final Future<Void> channelCloseFuture = (this.allChannels != null) ?
                this.allChannels.close() : new SucceededFuture<Void>(GlobalEventExecutor.INSTANCE, null);

        final Future<Void> disconnectFuture;

        if (this.shouldShutDownEventLoopGroup) {
            // Wait for the channel to close before we try to shut down the event loop group
            channelCloseFuture.addListener(new GenericFutureListener<Future<Void>>() {

                @Override
                public void operationComplete(final Future<Void> future) throws Exception {
                    MockApnsServer.this.bootstrap.config().group().shutdownGracefully();
                }
            });

            // Since the termination future for the event loop group is a Future<?> instead of a Future<Void>,
            // we'll need to create our own promise and then notify it when the termination future completes.
            disconnectFuture = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);

            this.bootstrap.config().group().terminationFuture().addListener(new GenericFutureListener() {

                @Override
                public void operationComplete(final Future future) throws Exception {
                    ((Promise<Void>) disconnectFuture).trySuccess(null);
                }
            });
        } else {
            // We're done once we've closed all the channels, so we can return the closure future directly.
            disconnectFuture = channelCloseFuture;
        }

        disconnectFuture.addListener(new GenericFutureListener<Future<Void>>() {
            @Override
            public void operationComplete(final Future<Void> future) throws Exception {
                if (MockApnsServer.this.sslContext instanceof ReferenceCounted) {
                    if (MockApnsServer.this.hasReleasedSslContext.compareAndSet(false, true)) {
                        ((ReferenceCounted) MockApnsServer.this.sslContext).release();
                    }
                }
            }
        });

        return disconnectFuture;
    }
}
