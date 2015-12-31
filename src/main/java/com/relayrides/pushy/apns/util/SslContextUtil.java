package com.relayrides.pushy.apns.util;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLException;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;

/**
 * A utility class for creating SSL contexts for use with an {@link ApnsClient}.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class SslContextUtil {

    public static SslContext createDefaultSslContext(final File certificateFile, final File privateKeyFile, final String privateKeyPassword) throws SSLException {
        return getDefaultBuilder()
                .keyManager(certificateFile, privateKeyFile, privateKeyPassword)
                .build();
    }

    public static SslContext createDefaultSslContext(final X509Certificate certificate, final PrivateKey privateKey, final String privateKeyPassword) throws SSLException {
        return getDefaultBuilder()
                .keyManager(privateKey, privateKeyPassword, certificate)
                .build();
    }

    private static SslContextBuilder getDefaultBuilder() {
        return SslContextBuilder.forClient()
                .sslProvider(OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(Protocol.ALPN,
                        SelectorFailureBehavior.NO_ADVERTISE,
                        SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2));
    }
}
