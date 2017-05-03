package com.relayrides.pushy.apns;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.SucceededFuture;

import javax.net.ssl.SSLException;
import java.io.InputStream;

/**
 * A simple benchmark server that sends a "looks good!" response to any and all requests.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
class BenchmarkApnsServer {

    private final ServerBootstrap bootstrap;
    private ChannelGroup allChannels;

    private static final int MAX_CONCURRENT_STREAMS = 500;

    public BenchmarkApnsServer(final InputStream certificateChainInputStream, final InputStream privateKeyPkcs8InputStream, final NioEventLoopGroup eventLoopGroup) throws SSLException {
        final SslContext sslContext;
        {
            final SslProvider sslProvider;

            if (OpenSsl.isAvailable()) {
                if (OpenSsl.isAlpnSupported()) {
                    sslProvider = SslProvider.OPENSSL;
                } else {
                    sslProvider = SslProvider.JDK;
                }
            } else {
                sslProvider = SslProvider.JDK;
            }

            sslContext = SslContextBuilder.forServer(certificateChainInputStream, privateKeyPkcs8InputStream, null)
                    .sslProvider(sslProvider)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .clientAuth(ClientAuth.OPTIONAL)
                    .applicationProtocolConfig(new ApplicationProtocolConfig(
                            ApplicationProtocolConfig.Protocol.ALPN,
                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2))
                    .build();
        }

        this.bootstrap = new ServerBootstrap();
        this.bootstrap.group(eventLoopGroup);

        this.bootstrap.channel(NioServerSocketChannel.class);
        this.bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel) throws Exception {
                final SslHandler sslHandler = sslContext.newHandler(channel.alloc());
                channel.pipeline().addLast(sslHandler);
                channel.pipeline().addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {

                    @Override
                    protected void configurePipeline(final ChannelHandlerContext context, final String protocol) throws Exception {
                        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                            context.pipeline().addLast(new BenchmarkApnsServerHandler.BenchmarkApnsServerHandlerBuilder()
                                    .initialSettings(new Http2Settings().maxConcurrentStreams(MAX_CONCURRENT_STREAMS))
                                    .build());

                            BenchmarkApnsServer.this.allChannels.add(context.channel());
                        } else {
                            throw new IllegalStateException("Unexpected protocol: " + protocol);
                        }
                    }
                });
            }
        });
    }

    public Future<Void> start(final int port) {
        final ChannelFuture channelFuture = this.bootstrap.bind(port);

        this.allChannels = new DefaultChannelGroup(channelFuture.channel().eventLoop(), true);
        this.allChannels.add(channelFuture.channel());

        return channelFuture;
    }

    public Future<Void> shutdown() {
        return (this.allChannels != null) ? this.allChannels.close() : new SucceededFuture<Void>(GlobalEventExecutor.INSTANCE, null);
    }
}
