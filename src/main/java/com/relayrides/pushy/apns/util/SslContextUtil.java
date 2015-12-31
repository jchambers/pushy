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

    /**
     * Creates a new SSL context for an {@link ApnsClient} with a certificate and key from the given files. The
     * certificate file <em>must</em> contain a PEM-formatted X.509 certificate, and the key file <em>must</em> contain
     * a PKCS8-formatted private key.
     *
     * @param certificatePemFile a PEM-formatted file containing an X.509 certificate to be used to identify the client
     * to the APNs server
     * @param privateKeyPkcs8File a PKCS8-formatted file containing a private key for the client certificate
     * @param privateKeyPassword the password to be used to decrypt the private key; may be {@code null} if the private
     * key does not require a password
     *
     * @return an SSL context with the given client credentials suitable for use with an {@link ApnsClient}
     *
     * @throws SSLException if the given key or certificate could not be loaded or if any other SSL-related problem
     * arises when constructing the context
     */
    public static SslContext createDefaultSslContext(final File certificatePemFile, final File privateKeyPkcs8File, final String privateKeyPassword) throws SSLException {
        return getDefaultBuilder()
                .keyManager(certificatePemFile, privateKeyPkcs8File, privateKeyPassword)
                .build();
    }

    /**
     * Creates a new SSL context for an {@link ApnsClient} with the given certificate private key.
     *
     * @param certificate the certificate to be used to identify the client to the APNs server
     * @param privateKey the private key for the client certificate
     * @param privateKeyPassword the password to be used to decrypt the private key; may be {@code null} if the private
     * key does not require a password
     *
     * @return an SSL context with the given client credentials suitable for use with an {@link ApnsClient}
     *
     * @throws SSLException if the given key or certificate could not be loaded or if any other SSL-related problem
     * arises when constructing the context
     */
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
