/* Copyright (c) 2013-2016 RelayRides
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
 * THE SOFTWARE. */

package com.relayrides.pushy.apns;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.relayrides.pushy.apns.proxy.ProxyHandlerFactory;

import io.netty.channel.EventLoopGroup;
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
 * <p>An {@code ApnsClientBuilder} constructs new {@link ApnsClient} instances. Callers must supply client credentials
 * via one of the {@code setClientCredentials} methods prior to constructing a new client with the
 * {@link com.relayrides.pushy.apns.ApnsClientBuilder#build()} method; all other settings are optional.</p>
 *
 * <p>Client builders may be reused to generate multiple clients, and their settings may be changed from one client to
 * the next.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @param <T> the type of notification handled by client instances created by this builder
 */
public class ApnsClientBuilder<T extends ApnsPushNotification> {
    private X509Certificate clientCertificate;
    private PrivateKey privateKey;
    private String privateKeyPassword;

    private File trustedServerCertificatePemFile;

    private EventLoopGroup eventLoopGroup;

    private ApnsClientMetricsListener metricsListener;

    private ProxyHandlerFactory proxyHandlerFactory;

    private Long connectionTimeout;
    private TimeUnit connectionTimeoutUnit;

    private Long writeTimeout;
    private TimeUnit writeTimeoutUnit;

    private Long gracefulShutdownTimeout;
    private TimeUnit gracefulShutdownTimeoutUnit;

    private static final Logger log = LoggerFactory.getLogger(ApnsClientBuilder.class);

    /**
     * <p>Sets the credentials for the client to be built using the contents of the given PKCS#12 file. The PKCS#12 file
     * <em>must</em> contain a single certificate/private key pair.</p>
     *
     * @param p12File a PKCS#12-formatted file containing the certificate and private key to be used to identify the
     * client to the APNs server
     * @param p12Password the password to be used to decrypt the contents of the given PKCS#12 file; passwords may be
     * blank (i.e. {@code ""}), but must not be {@code null}
     *
     * @throws SSLException if the given PKCS#12 file could not be loaded or if any other SSL-related problem arises
     * when constructing the context
     * @throws IOException if any IO problem occurs while attempting to read the given PKCS#12 file, or the PKCS#12 file
     * could not be found
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder<T> setClientCredentials(final File p12File, final String p12Password) throws SSLException, IOException {
        try (final InputStream p12InputStream = new FileInputStream(p12File)) {
            return this.setClientCredentials(p12InputStream, p12Password);
        }
    }

    /**
     * <p>Sets the credentials for the client to be built using the data from the given PKCS#12 input stream. The
     * PKCS#12 data <em>must</em> contain a single certificate/private key pair.</p>
     *
     * @param p12File an input stream to a PKCS#12-formatted file containing the certificate and private key to be used
     * to identify the client to the APNs server
     * @param p12Password the password to be used to decrypt the contents of the given PKCS#12 file; passwords may be
     * blank (i.e. {@code ""}), but must not be {@code null}
     *
     * @throws SSLException if the given PKCS#12 file could not be loaded or if any other SSL-related problem arises
     * when constructing the context
     * @throws IOException if any IO problem occurs while attempting to read the given PKCS#12 file, or the PKCS#12 file
     * could not be found
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder<T> setClientCredentials(final InputStream p12InputStream, final String p12Password) throws SSLException, IOException {
        final X509Certificate x509Certificate;
        final PrivateKey privateKey;

        try {
            final PrivateKeyEntry privateKeyEntry = P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, p12Password);

            final Certificate certificate = privateKeyEntry.getCertificate();

            if (!(certificate instanceof X509Certificate)) {
                throw new KeyStoreException("Found a certificate in the provided PKCS#12 file, but it was not an X.509 certificate.");
            }

            x509Certificate = (X509Certificate) certificate;
            privateKey = privateKeyEntry.getPrivateKey();
        } catch (final KeyStoreException e) {
            throw new SSLException(e);
        }

        return this.setClientCredentials(x509Certificate, privateKey, p12Password);
    }

    /**
     * <p>Sets the credentials for the client to be built.</p>
     *
     * @param clientCertificate the certificate to be used to identify the client to the APNs server
     * @param privateKey the private key for the client certificate
     * @param privateKeyPassword the password to be used to decrypt the private key; may be {@code null} if the private
     * key does not require a password
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder<T> setClientCredentials(final X509Certificate clientCertificate, final PrivateKey privateKey, final String privateKeyPassword) {
        this.clientCertificate = clientCertificate;
        this.privateKey = privateKey;
        this.privateKeyPassword = privateKeyPassword;

        return this;
    }

    protected ApnsClientBuilder<T> setTrustedServerCertificate(final File trustedServerCertificatePemFile) {
        this.trustedServerCertificatePemFile = trustedServerCertificatePemFile;
        return this;
    }

    /**
     * <p>Sets the event loop group to be used by the client to be built. If not set (or if {@code null}), the client
     * will create and manage its own event loop group.</p>
     *
     * <p>Generally speaking, callers don't need to set event loop groups for clients, but it may be useful to specify
     * an event loop group under certain circumstances. In particular, specifying an event loop group that is shared
     * among multiple {@code ApnsClient} instances can keep thread counts manageable. Regardless of the number of
     * concurrent {@code ApnsClient} instances, callers may also wish to specify an event loop group to take advantage
     * of certain platform-specific optimizations (e.g. epoll event loop groups).</p>
     *
     * @param eventLoopGroup the event loop group to use for this client, or {@code null} to let the client manage its
     * own event loop group
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder<T> setEventLoopGroup(final EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        return this;
    }

    /**
     * Sets the metrics listener for the client to be built. Metrics listeners gather information that describes the
     * performance and behavior of a client, and are completely optional.
     *
     * @param metricsListener the metrics listener for the client under construction, or {@code null} if this client
     * should not report metrics to a listener
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder<T> setMetricsListener(final ApnsClientMetricsListener metricsListener) {
        this.metricsListener = metricsListener;
        return this;
    }

    public ApnsClientBuilder<T> setProxyHandlerFactory(final ProxyHandlerFactory proxyHandlerFactory) {
        this.proxyHandlerFactory = proxyHandlerFactory;
        return this;
    }

    public ApnsClientBuilder<T> setConnectionTimeout(final long connectionTimeout, final TimeUnit timeoutUnit) {
        this.connectionTimeout = connectionTimeout;
        this.connectionTimeoutUnit = timeoutUnit;

        return this;
    }

    public ApnsClientBuilder<T> setWriteTimeout(final long writeTimeout, final TimeUnit timeoutUnit) {
        this.writeTimeout = writeTimeout;
        this.writeTimeoutUnit = timeoutUnit;

        return this;
    }

    public ApnsClientBuilder<T> setGracefulShutdownTimeout(final long gracefulShutdownTimeout, final TimeUnit timeoutUnit) {
        this.gracefulShutdownTimeout = gracefulShutdownTimeout;
        this.gracefulShutdownTimeoutUnit = timeoutUnit;

        return this;
    }

    public ApnsClient<T> build() throws SSLException {
        Objects.requireNonNull(this.clientCertificate, "Client certificate must be set before building an APNs client.");
        Objects.requireNonNull(this.privateKey, "Private key must be set before building an APNs client.");

        final SslContext sslContext;
        {
            final SslProvider sslProvider;

            if (OpenSsl.isAvailable()) {
                if (OpenSsl.isAlpnSupported()) {
                    log.info("Native SSL provider is available and supports ALPN; will use native provider.");
                    sslProvider = SslProvider.OPENSSL;
                } else {
                    log.info("Native SSL provider is available, but does not support ALPN; will use JDK SSL provider.");
                    sslProvider = SslProvider.JDK;
                }
            } else {
                log.info("Native SSL provider not available; will use JDK SSL provider.");
                sslProvider = SslProvider.JDK;
            }

            final SslContextBuilder sslContextBuilder = SslContextBuilder.forClient()
                    .sslProvider(sslProvider)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .keyManager(this.privateKey, this.privateKeyPassword, this.clientCertificate)
                    .applicationProtocolConfig(
                            new ApplicationProtocolConfig(Protocol.ALPN,
                                    SelectorFailureBehavior.NO_ADVERTISE,
                                    SelectedListenerFailureBehavior.ACCEPT,
                                    ApplicationProtocolNames.HTTP_2));

            if (this.trustedServerCertificatePemFile != null) {
                sslContextBuilder.trustManager(this.trustedServerCertificatePemFile);
            }

            sslContext = sslContextBuilder.build();
        }

        final ApnsClient<T> apnsClient = new ApnsClient<T>(sslContext, this.eventLoopGroup);

        apnsClient.setMetricsListener(this.metricsListener);
        apnsClient.setProxyHandlerFactory(this.proxyHandlerFactory);

        if (this.connectionTimeout != null) {
            apnsClient.setConnectionTimeout((int) this.connectionTimeoutUnit.toMillis(this.connectionTimeout));
        }

        if (this.writeTimeout != null) {
            apnsClient.setWriteTimeout(this.writeTimeoutUnit.toMillis(this.writeTimeout));
        }

        if (this.gracefulShutdownTimeout != null) {
            apnsClient.setGracefulShutdownTimeout(this.gracefulShutdownTimeoutUnit.toMillis(this.gracefulShutdownTimeout));
        }

        return apnsClient;
    }
}