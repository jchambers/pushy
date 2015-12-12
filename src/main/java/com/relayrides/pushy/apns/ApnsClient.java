package com.relayrides.pushy.apns;

import static io.netty.handler.logging.LogLevel.INFO;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Security;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2SecurityUtil;
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

public class ApnsClient {

    private final String hostname;
    private final int port;

    private final SslContext sslContext;

    private final EventLoopGroup eventLoopGroup;

    private Channel channel;

    private static final String DEFAULT_ALGORITHM = "SunX509";

    public ApnsClient(final String hostname, final int port, final KeyStore keyStore, final String keyStorePassword, final EventLoopGroup eventLoopGroup) {
        this.hostname = hostname;
        this.port = port;
        this.eventLoopGroup = eventLoopGroup;

        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");

        if (algorithm == null) {
            algorithm = DEFAULT_ALGORITHM;
        }

        try {
            if (keyStore.size() == 0) {
                throw new KeyStoreException(
                        "Keystore is empty; while this is legal for keystores in general, APNs clients must have at least one key.");
            }

            final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
            trustManagerFactory.init(keyStore);

            final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
            keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

            this.sslContext = SslContextBuilder.forClient()
                    .sslProvider(OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .trustManager(trustManagerFactory).keyManager(keyManagerFactory)
                    .applicationProtocolConfig(
                            new ApplicationProtocolConfig(Protocol.ALPN, SelectorFailureBehavior.NO_ADVERTISE,
                                    SelectedListenerFailureBehavior.ACCEPT, ApplicationProtocolNames.HTTP_2))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize SSL context.", e);
        }
    }

    public ChannelFuture connect() {
        final Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(this.eventLoopGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.remoteAddress(this.hostname, this.port);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel) throws Exception {
                final ChannelPipeline pipeline = channel.pipeline();
                pipeline.addLast(ApnsClient.this.sslContext.newHandler(channel.alloc()));

                pipeline.addLast(new ApplicationProtocolNegotiationHandler("") {
                    @Override
                    protected void configurePipeline(final ChannelHandlerContext context, final String protocol) {
                        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                            context.pipeline()
                            .addLast(new ApnsClientHandler.Builder()
                                    .frameLogger(new Http2FrameLogger(INFO, ApnsClient.class)).server(false)
                                    .encoderEnforceMaxConcurrentStreams(true).build());
                        } else {
                            context.close();
                            throw new IllegalStateException("unknown protocol: " + protocol);
                        }
                    }
                });
            }
        });

        final ChannelFuture channelFuture = bootstrap.connect();
        this.channel = channelFuture.channel();

        return channelFuture;
    }

    public ChannelFuture close() {
        // TODO Graceful shutdown things
        return this.channel.close();
    }
}
