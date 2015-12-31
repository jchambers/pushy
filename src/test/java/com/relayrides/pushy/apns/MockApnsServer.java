package com.relayrides.pushy.apns;

import java.io.File;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.net.ssl.SSLSession;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;

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
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;

public class MockApnsServer {

    private final ServerBootstrap bootstrap;

    final Map<String, Map<String, Date>> tokenExpirationsByTopic = new HashMap<String, Map<String, Date>>();

    private ChannelGroup allChannels;

    private static final String CA_CERTIFICATE_FILENAME = "/ca.crt";
    private static final String SERVER_CERTIFICATE_FILENAME = "/server.crt";
    private static final String SERVER_PRIVATE_KEY_FILENAME = "/server.pk8";

    private static final String TOPIC_OID = "1.2.840.113635.100.6.3.6";

    public MockApnsServer(final EventLoopGroup eventLoopGroup) {
        final SslContext sslContext;
        try {
            final File caCertificateFile = new File(MockApnsServer.class.getResource(CA_CERTIFICATE_FILENAME).toURI());
            final File serverCertificateFile = new File(MockApnsServer.class.getResource(SERVER_CERTIFICATE_FILENAME).toURI());
            final File serverPrivateKeyFile = new File(MockApnsServer.class.getResource(SERVER_PRIVATE_KEY_FILENAME).toURI());

            sslContext = SslContextBuilder.forServer(serverCertificateFile, serverPrivateKeyFile)
                    .sslProvider(OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .trustManager(caCertificateFile)
                    .clientAuth(ClientAuth.REQUIRE)
                    .applicationProtocolConfig(new ApplicationProtocolConfig(
                            Protocol.ALPN,
                            SelectorFailureBehavior.NO_ADVERTISE,
                            SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2))
                    .build();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to create SSL context for mock APNs server.", e);
        }

        this.bootstrap = new ServerBootstrap();
        this.bootstrap.group(eventLoopGroup);
        this.bootstrap.channel(NioServerSocketChannel.class);
        this.bootstrap.handler(new LoggingHandler(LogLevel.INFO));
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
                            final Set<String> topics = new HashSet<String>();
                            {
                                final SSLSession sslSession = sslHandler.engine().getSession();

                                for (final String keyValuePair : sslSession.getPeerPrincipal().getName().split(",")) {
                                    if (keyValuePair.toLowerCase().startsWith("uid=")) {
                                        topics.add(keyValuePair.substring(4));
                                        break;
                                    }
                                }

                                for (final Certificate certificate : sslSession.getPeerCertificates()) {
                                    if (certificate instanceof X509Certificate) {
                                        final X509Certificate x509Certificate = (X509Certificate) certificate;
                                        final byte[] topicExtensionData = x509Certificate.getExtensionValue(TOPIC_OID);

                                        if (topicExtensionData != null) {
                                            final ASN1Primitive extensionValue =
                                                    JcaX509ExtensionUtils.parseExtensionValue(topicExtensionData);

                                            if (extensionValue instanceof ASN1Sequence) {
                                                final ASN1Sequence sequence = (ASN1Sequence) extensionValue;

                                                for (int i = 0; i < sequence.size(); i++) {
                                                    if (sequence.getObjectAt(i) instanceof ASN1String) {
                                                        topics.add(sequence.getObjectAt(i).toString());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                            }

                            context.pipeline().addLast(new MockApnsServerHandler.Builder()
                                    .apnsServer(MockApnsServer.this)
                                    .topics(topics)
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
