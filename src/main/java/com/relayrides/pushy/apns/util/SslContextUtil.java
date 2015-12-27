package com.relayrides.pushy.apns.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
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

    private static final String DEFAULT_ALGORITHM = "SunX509";

    /**
     * Creates a new SSL context using the JVM default trust managers and the certificates in the given PKCS12 file.
     *
     * @param pathToPKCS12File the path to a PKCS12 file that contains the client certificate
     * @param keystorePassword the password to read the PKCS12 file; may be {@code null}
     *
     * @return an SSL context configured with the given client certificate and the JVM default trust managers
     */
    public static SslContext createDefaultSslContext(final String pathToPKCS12File, final String keystorePassword) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException, IOException {
        try (final FileInputStream keystoreInputStream = new FileInputStream(pathToPKCS12File)) {
            return createDefaultSslContext(keystoreInputStream, keystorePassword);
        }
    }

    /**
     * Creates a new SSL context using the JVM default trust managers and the certificates in the given PKCS12 InputStream.
     *
     * @param keystoreInputStream a PKCS12 file that contains the client certificate
     * @param keystorePassword the password to read the PKCS12 file; may be {@code null}
     *
     * @return an SSL context configured with the given client certificate and the JVM default trust managers
     */
    public static SslContext createDefaultSslContext(final InputStream keystoreInputStream, final String keystorePassword) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException, IOException {
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        final char[] password = keystorePassword != null ? keystorePassword.toCharArray() : null;

        keyStore.load(keystoreInputStream, password);

        return createDefaultSslContext(keyStore, keystorePassword != null ? keystorePassword.toCharArray() : null);
    }

    /**
     * Creates a new SSL context using the JVM default trust managers and the certificates in the given keystore.
     *
     * @param keyStore A {@code KeyStore} containing the client certificates to present during a TLS handshake. The
     * {@code KeyStore} should be loaded before being used here.
     * @param keyStorePassword a password to unlock the given {@code KeyStore}; may be {@code null}
     *
     * @return an SSL context configured with the certificates in the given keystore and the JVM default trust managers
     */
    public static SslContext createDefaultSslContext(final KeyStore keyStore, final char[] keyStorePassword) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException, SSLException {
        final String algorithm;
        {
            final String algorithmFromSecurityProperties = Security.getProperty("ssl.KeyManagerFactory.algorithm");
            algorithm = algorithmFromSecurityProperties != null ? algorithmFromSecurityProperties : DEFAULT_ALGORITHM;
        }

        if (keyStore.size() == 0) {
            throw new KeyStoreException("Keystore is empty; while this is legal for keystores in general, APNs clients must have at least one key.");
        }

        final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
        keyManagerFactory.init(keyStore, keyStorePassword);

        return SslContextBuilder.forClient()
                .sslProvider(OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .keyManager(keyManagerFactory)
                .applicationProtocolConfig(new ApplicationProtocolConfig(Protocol.ALPN,
                        SelectorFailureBehavior.NO_ADVERTISE,
                        SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2))
                .build();
    }
}
