package com.relayrides.pushy.apns;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

public class MockApnsServer {

    private final ServerBootstrap bootstrap;

    final Map<String, Map<String, Date>> tokenExpirationsByTopic = new HashMap<>();

    private ChannelGroup allChannels;

    public MockApnsServer(final SslContext sslContext, final EventLoopGroup eventLoopGroup) {
        this.bootstrap = new ServerBootstrap();
        this.bootstrap.group(eventLoopGroup);
        this.bootstrap.channel(NioServerSocketChannel.class);
        this.bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel) throws Exception {
                final
                SslHandler sslHandler = sslContext.newHandler(channel.alloc());
                channel.pipeline().addLast(sslHandler);
                channel.pipeline().addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {

                    @Override
                    protected void configurePipeline(final ChannelHandlerContext context, final String protocol) throws Exception {
                        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                            context.pipeline().addLast(new MockApnsServerHandler.MockApnsServerHandlerBuilder()
                                    .apnsServer(MockApnsServer.this)
                                    .initialSettings(new Http2Settings().maxConcurrentStreams(8))
                                    .build());

                            MockApnsServer.this.allChannels.add(context.channel());
                        } else {
                            throw new IllegalStateException("Unexpected protocol: " + protocol);
                        }
                    }
                });
            }
        });
    }

    public ChannelFuture start(final int port) {
        final ChannelFuture channelFuture = this.bootstrap.bind(port);

        this.allChannels = new DefaultChannelGroup(channelFuture.channel().eventLoop(), true);
        this.allChannels.add(channelFuture.channel());

        return channelFuture;
    }

    public void registerToken(final String topic, final String token) {
        this.registerToken(topic, token, null);
    }

    public void registerToken(final String topic, final String token, final Date expiration) {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(token);

        if (!this.tokenExpirationsByTopic.containsKey(topic)) {
            this.tokenExpirationsByTopic.put(topic, new HashMap<String, Date>());
        }

        this.tokenExpirationsByTopic.get(topic).put(token, expiration);
    }

    protected boolean isTokenRegisteredForTopic(final String token, final String topic) {
        final Map<String, Date> tokensWithinTopic = this.tokenExpirationsByTopic.get(topic);

        return tokensWithinTopic != null && tokensWithinTopic.containsKey(token);
    }

    protected Date getExpirationTimestampForTokenInTopic(final String token, final String topic) {
        final Map<String, Date> tokensWithinTopic = this.tokenExpirationsByTopic.get(topic);

        return tokensWithinTopic != null ? tokensWithinTopic.get(token) : null;
    }

    public ChannelGroupFuture shutdown() {
        return this.allChannels.close();
    }
}
