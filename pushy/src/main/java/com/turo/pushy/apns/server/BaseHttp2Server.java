/*
 * Copyright (c) 2013-2018 Turo
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
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSession;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class BaseHttp2Server {
    private final SslContext sslContext;
    private final AtomicBoolean hasReleasedSslContext = new AtomicBoolean(false);

    private final ServerBootstrap bootstrap;
    private final boolean shouldShutDownEventLoopGroup;

    private ChannelGroup allChannels;

    private static final Logger log = LoggerFactory.getLogger(BaseHttp2Server.class);

    @ChannelHandler.Sharable
    private static class ConnectionNegotiationErrorHandler extends ChannelHandlerAdapter {

        static final ConnectionNegotiationErrorHandler INSTANCE = new ConnectionNegotiationErrorHandler();

        @Override
        public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
            log.debug("Server caught an exception before establishing an HTTP/2 connection.", cause);
        }
    }

    BaseHttp2Server(final SslContext sslContext, final EventLoopGroup eventLoopGroup) {

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

        this.bootstrap.channel(ServerChannelClassUtil.getServerSocketChannelClass(this.bootstrap.config().group()));
        this.bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel) {
                final SslHandler sslHandler = sslContext.newHandler(channel.alloc());
                channel.pipeline().addLast(sslHandler);
                channel.pipeline().addLast(ConnectionNegotiationErrorHandler.INSTANCE);

                sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<Channel>>() {
                    @Override
                    public void operationComplete(final Future<Channel> handshakeFuture) throws Exception {
                        if (handshakeFuture.isSuccess()) {
                            BaseHttp2Server.this.addHandlersToPipeline(sslHandler.engine().getSession(), channel.pipeline());
                            channel.pipeline().remove(ConnectionNegotiationErrorHandler.INSTANCE);

                            BaseHttp2Server.this.allChannels.add(channel);
                        } else {
                            log.debug("TLS handshake failed.", handshakeFuture.cause());
                        }
                    }
                });
            }
        });
    }

    protected abstract void addHandlersToPipeline(final SSLSession sslSession, final ChannelPipeline pipeline) throws Exception;

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
                    BaseHttp2Server.this.bootstrap.config().group().shutdownGracefully();
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
                if (BaseHttp2Server.this.sslContext instanceof ReferenceCounted) {
                    if (BaseHttp2Server.this.hasReleasedSslContext.compareAndSet(false, true)) {
                        ((ReferenceCounted) BaseHttp2Server.this.sslContext).release();
                    }
                }
            }
        });

        return disconnectFuture;
    }
}
