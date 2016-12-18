package com.relayrides.pushy.apns;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import com.relayrides.pushy.apns.proxy.ProxyHandlerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class ApnsChannelPool extends FixedChannelPool {

    private final ApnsClient apnsClient;
    private final SslContext sslContext;
    private final EventLoopGroup eventLoopGroup;

    private final ChannelGroup allChannels;

    private int connectTimeoutMillis;
    private long writeTimeoutMillis = ApnsClient.DEFAULT_WRITE_TIMEOUT_MILLIS;
    private long gracefulShutdownTimeoutMillis;
    private WriteBufferWaterMark writeBufferWaterMark;
    private ProxyHandlerFactory proxyHandlerFactory;

    static final int PING_IDLE_TIME_MILLIS = 60_000; // milliseconds

    private static class NoopChannelPoolHandler implements ChannelPoolHandler {

        @Override
        public void channelReleased(final Channel ch) throws Exception {
        }

        @Override
        public void channelAcquired(final Channel ch) throws Exception {
        }

        @Override
        public void channelCreated(final Channel ch) throws Exception {
        }
    }

    public ApnsChannelPool(final ApnsClient apnsClient, final SocketAddress apnsServerAddress, final SslContext sslContext, final EventLoopGroup eventLoopGroup, final int maxChannels) {
        super(new Bootstrap()
                .group(eventLoopGroup)
                .channel(SocketChannelClassUtil.getSocketChannelClass(eventLoopGroup))
                .remoteAddress(apnsServerAddress)
                .option(ChannelOption.TCP_NODELAY, true),
                new NoopChannelPoolHandler(), maxChannels);

        this.apnsClient = apnsClient;
        this.sslContext = sslContext;
        this.eventLoopGroup = eventLoopGroup;

        // TODO Make this stay closed if we're using a managed event loop group?
        this.allChannels = new DefaultChannelGroup(eventLoopGroup.next());
    }

    protected void setConnectionTimeout(final int timeoutMillis) {
        this.connectTimeoutMillis = timeoutMillis;
    }

    protected void setChannelWriteBufferWatermark(final WriteBufferWaterMark writeBufferWaterMark) {
        this.writeBufferWaterMark = writeBufferWaterMark;
    }

    protected void setProxyHandlerFactory(final ProxyHandlerFactory proxyHandlerFactory) {
        this.proxyHandlerFactory = proxyHandlerFactory;
    }

    protected void setWriteTimeout(final long writeTimeoutMillis) {
        this.writeTimeoutMillis = writeTimeoutMillis;
    }

    protected void setGracefulShutdownTimeout(final long timeoutMillis) {
        this.gracefulShutdownTimeoutMillis = timeoutMillis;
    }

    protected EventLoopGroup getEventLoopGroup() {
        return this.eventLoopGroup;
    }

    @Override
    protected ChannelFuture connectChannel(final Bootstrap bootstrap) {
        // TODO Notify listener that we're creating a new channel

        if (this.connectTimeoutMillis > 0) {
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, this.connectTimeoutMillis);
        }

        if (this.writeBufferWaterMark != null) {
            bootstrap.option(ChannelOption.WRITE_BUFFER_WATER_MARK, this.writeBufferWaterMark);
        }

        final ProxyHandlerFactory proxyHandlerFactory = this.proxyHandlerFactory;
        bootstrap.resolver(proxyHandlerFactory == null ? DefaultAddressResolverGroup.INSTANCE : NoopAddressResolverGroup.INSTANCE);

        // This is a little circuitous; we ultimately want to return a ChannelPromise (ChannelFuture), but to construct
        // one, we need a channel. We won't have the channel until call `bootstrap.connect()`, but after that, it's too
        // late to change the handler, and the handler needs to know which promise to notify when it's done configuring
        // its pipeline. Gross. So this is a bit of a Rube Goldberg machine to bridge the gap.
        final DefaultPromise<Void> pipelineConfiguredPromise = new DefaultPromise<>(bootstrap.config().group().next());

        bootstrap.handler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel) throws Exception {
                final ChannelPipeline pipeline = channel.pipeline();

                if (proxyHandlerFactory != null) {
                    pipeline.addFirst(proxyHandlerFactory.createProxyHandler());
                }

                if (ApnsChannelPool.this.writeTimeoutMillis > 0) {
                    pipeline.addLast(new WriteTimeoutHandler(ApnsChannelPool.this.writeTimeoutMillis, TimeUnit.MILLISECONDS));
                }

                pipeline.addLast(ApnsChannelPool.this.sslContext.newHandler(channel.alloc()));
                pipeline.addLast(new ApplicationProtocolNegotiationHandler("") {
                    @Override
                    protected void configurePipeline(final ChannelHandlerContext context, final String protocol) {
                        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                            final ApnsClientHandler apnsClientHandler = new ApnsClientHandler.ApnsClientHandlerBuilder()
                                    .server(false)
                                    .apnsClient(ApnsChannelPool.this.apnsClient)
                                    .authority(((InetSocketAddress) context.channel().remoteAddress()).getHostName())
                                    .encoderEnforceMaxConcurrentStreams(true)
                                    .build();

                            if (ApnsChannelPool.this.gracefulShutdownTimeoutMillis > 0) {
                                apnsClientHandler.gracefulShutdownTimeoutMillis(ApnsChannelPool.this.gracefulShutdownTimeoutMillis);
                            }

                            context.pipeline().addLast(new IdleStateHandler(0, 0, PING_IDLE_TIME_MILLIS, TimeUnit.MILLISECONDS));
                            context.pipeline().addLast(apnsClientHandler);

                            pipelineConfiguredPromise.trySuccess(null);
                        } else {
                            throw new IllegalArgumentException("Unexpected protocol: " + protocol);
                        }
                    }
                });
            }
        });

        final ChannelFuture connectFuture = bootstrap.connect().addListener(new GenericFutureListener<ChannelFuture> () {

            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                // A successful connection attempt is necessary, but not sufficient here. If something goes wrong with
                // the connection attempt, we want to fail the overall "connect and configure" promise, but we'll have
                // to wait for more things to happen after connection before we can declare victory.
                if (!future.isSuccess()) {
                    pipelineConfiguredPromise.tryFailure(future.cause());
                }
            }
        });

        connectFuture.channel().closeFuture().addListener(new GenericFutureListener<ChannelFuture>() {

            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                pipelineConfiguredPromise.tryFailure(new RuntimeException("Channel closed before HTTP/2 handshake completed."));
            }});

        final DefaultChannelPromise connectAndConfigurePromise = new DefaultChannelPromise(connectFuture.channel());

        pipelineConfiguredPromise.addListener(new GenericFutureListener<Future<Void>>() {

            @Override
            public void operationComplete(final Future<Void> future) throws Exception {
                if (future.isSuccess()) {
                    connectAndConfigurePromise.setSuccess();
                } else {
                    connectAndConfigurePromise.setFailure(future.cause());
                }
            }
        });

        return connectAndConfigurePromise;
    }

    public Future<Void> shutdown() {
        return this.allChannels.close();
    }

    @Override
    public void close() {
        super.close();

        this.shutdown();
    }
}
