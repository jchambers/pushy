package com.relayrides.pushy.apns;

import static io.netty.handler.logging.LogLevel.INFO;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.Security;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;

public class MockApnsServer {

    private final int port;
    private final SslContext sslContext;

    private final EventLoopGroup eventLoopGroup;

    private final Set<String> registeredTokens = new HashSet<>();

    private ChannelGroup allChannels;

    private static final String SERVER_KEYSTORE_FILE_NAME = "/pushy-test-server.jks";
    private static final char[] KEYSTORE_PASSWORD = "pushy-test".toCharArray();

    private static final String DEFAULT_ALGORITHM = "SunX509";

    public MockApnsServer(final int port, final EventLoopGroup eventLoopGroup) {
        this.port = port;
        this.eventLoopGroup = eventLoopGroup;

        try (final InputStream keyStoreInputStream = MockApnsServer.class.getResourceAsStream(SERVER_KEYSTORE_FILE_NAME)) {
            final KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(keyStoreInputStream, KEYSTORE_PASSWORD);

            String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");

            if (algorithm == null) {
                algorithm = DEFAULT_ALGORITHM;
            }

            final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
            keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD);

            final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
            trustManagerFactory.init(keyStore);

            final SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
            this.sslContext = SslContextBuilder.forServer(keyManagerFactory)
                    .sslProvider(provider)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .keyManager(keyManagerFactory)
                    .trustManager(trustManagerFactory)
                    .applicationProtocolConfig(new ApplicationProtocolConfig(
                            Protocol.ALPN,
                            SelectorFailureBehavior.NO_ADVERTISE,
                            SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2))
                    .build();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to create SSL context for mock APNs server.", e);
        }
    }

    public ChannelFuture start() {
        final ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.option(ChannelOption.SO_BACKLOG, 1024);
        bootstrap.group(this.eventLoopGroup);
        bootstrap.channel(NioServerSocketChannel.class);
        bootstrap.handler(new LoggingHandler(LogLevel.INFO));
        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel) throws Exception {
                channel.pipeline().addLast(MockApnsServer.this.sslContext.newHandler(channel.alloc()));
                channel.pipeline().addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {

                    @Override
                    protected void configurePipeline(final ChannelHandlerContext context, final String protocol) throws Exception {
                        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                            context.pipeline().addLast(new MockApnsServerHandler.Builder()
                                    .frameLogger(new Http2FrameLogger(INFO, MockApnsServer.class))
                                    .apnsServer(MockApnsServer.this)
                                    .build());

                            MockApnsServer.this.allChannels.add(context.channel());
                        } else {
                            throw new IllegalStateException("Unexpected protocol: " + protocol);
                        }
                    }
                });
            }
        });

        final ChannelFuture channelFuture = bootstrap.bind(this.port);

        this.allChannels = new DefaultChannelGroup(channelFuture.channel().eventLoop(), true);
        this.allChannels.add(channelFuture.channel());

        return channelFuture;
    }

    public void registerToken(final String token) {
        this.registeredTokens.add(token.toLowerCase());
    }

    protected boolean isTokenRegistered(final String token) {
        return this.registeredTokens.contains(token.toLowerCase());
    }

    public void reset() {
        this.registeredTokens.clear();
    }

    public ChannelGroupFuture shutdown() {
        return this.allChannels.close();
    }
}
