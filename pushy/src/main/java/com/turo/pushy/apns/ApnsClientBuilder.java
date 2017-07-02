/*
 * Copyright (c) 2013-2017 Turo
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
 * THE SOFTWARE.
 */

package com.turo.pushy.apns;

import com.turo.pushy.apns.auth.ApnsSigningKey;
import com.turo.pushy.apns.proxy.ProxyHandlerFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * An {@code ApnsClientBuilder} constructs new {@link ApnsClient} instances. All settings are optional. Client builders
 * may be reused to generate multiple clients, and their settings may be changed from one client to the next.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public class ApnsClientBuilder {
    private InetSocketAddress apnsServerAddress;

    private X509Certificate clientCertificate;
    private PrivateKey privateKey;
    private String privateKeyPassword;

    private ApnsSigningKey signingKey;

    private File trustedServerCertificatePemFile;
    private InputStream trustedServerCertificateInputStream;
    private X509Certificate[] trustedServerCertificates;

    private EventLoopGroup eventLoopGroup;

    private int concurrentConnections = 1;

    private ApnsClientMetricsListener metricsListener;

    private ProxyHandlerFactory proxyHandlerFactory;

    private int connectionTimeoutMillis;
    private long idlePingIntervalMillis = DEFAULT_PING_IDLE_TIME_MILLIS;
    private long gracefulShutdownTimeoutMillis;

    /**
     * The default idle time in milliseconds after which the client will send a PING frame to the APNs server.
     *
     * @since 0.11
     */
    public static final int DEFAULT_PING_IDLE_TIME_MILLIS = 60_000;

    /**
     * The hostname for the production APNs gateway.
     *
     * @since 0.5
     */
    public static final String PRODUCTION_APNS_HOST = "api.push.apple.com";

    /**
     * The hostname for the development APNs gateway.
     *
     * @since 0.5
     */
    public static final String DEVELOPMENT_APNS_HOST = "api.development.push.apple.com";

    /**
     * The default (HTTPS) port for communication with the APNs gateway.
     *
     * @since 0.5
     */
    public static final int DEFAULT_APNS_PORT = 443;

    /**
     * <p>An alternative port for communication with the APNs gateway. According to Apple's documentation:</p>
     *
     * <blockquote>You can alternatively use port 2197 when communicating with APNs. You might do this, for example, to
     * allow APNs traffic through your firewall but to block other HTTPS traffic.</blockquote>
     *
     * @see <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/CommunicatingwithAPNs.html#//apple_ref/doc/uid/TP40008194-CH11-SW1">Communicating
     * with APNs</a>
     *
     * @since 0.5
     */
    public static final int ALTERNATE_APNS_PORT = 2197;

    /**
     * Sets the hostname of the server to which the client under construction will connect. Apple provides a production
     * and development environment.
     *
     * @param hostname the hostname of the server to which the client under construction should connect
     *
     * @return a reference to this builder
     *
     * @see ApnsClientBuilder#DEVELOPMENT_APNS_HOST
     * @see ApnsClientBuilder#PRODUCTION_APNS_HOST
     *
     * @see <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/CommunicatingwithAPNs.html#//apple_ref/doc/uid/TP40008194-CH11-SW1">Communicating with APNs</a>
     *
     * @since 0.11
     */
    public ApnsClientBuilder setApnsServer(final String hostname) {
        return this.setApnsServer(hostname, DEFAULT_APNS_PORT);
    }

    /**
     * Sets the hostname and port of the server to which the client under construction will connect. Apple provides a
     * production and development environment, both of which listen for traffic on the default HTTPS port
     * ({@value DEFAULT_APNS_PORT}) and an alternate port ({@value ALTERNATE_APNS_PORT}), which callers may use to work
     * around firewall or proxy restrictions.
     *
     * @param hostname the hostname of the server to which the client under construction should connect
     * @param port the port to which the client under contruction should connect
     *
     * @return a reference to this builder
     *
     * @see ApnsClientBuilder#DEVELOPMENT_APNS_HOST
     * @see ApnsClientBuilder#PRODUCTION_APNS_HOST
     * @see ApnsClientBuilder#DEFAULT_APNS_PORT
     * @see ApnsClientBuilder#ALTERNATE_APNS_PORT
     *
     * @see <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/CommunicatingwithAPNs.html#//apple_ref/doc/uid/TP40008194-CH11-SW1">Communicating with APNs</a>
     *
     * @since 0.11
     */
    public ApnsClientBuilder setApnsServer(final String hostname, final int port) {
        this.apnsServerAddress = new InetSocketAddress(hostname, port);
        return this;
    }

    /**
     * <p>Sets the TLS credentials for the client under construction using the contents of the given PKCS#12 file.
     * Clients constructed with TLS credentials will use TLS-based authentication when sending push notifications. The
     * PKCS#12 file <em>must</em> contain a certificate/private key pair.</p>
     *
     * <p>Clients may not have both TLS credentials and a signing key.</p>
     *
     * @param p12File a PKCS#12-formatted file containing the certificate and private key to be used to identify the
     * client to the APNs server
     * @param p12Password the password to be used to decrypt the contents of the given PKCS#12 file; passwords may be
     * blank (i.e. {@code ""}), but must not be {@code null}
     *
     * @throws SSLException if the given PKCS#12 file could not be loaded or if any other SSL-related problem arises
     * when constructing the context
     * @throws IOException if any IO problem occurred while attempting to read the given PKCS#12 file, or the PKCS#12
     * file could not be found
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder setClientCredentials(final File p12File, final String p12Password) throws SSLException, IOException {
        try (final InputStream p12InputStream = new FileInputStream(p12File)) {
            return this.setClientCredentials(p12InputStream, p12Password);
        }
    }

    /**
     * <p>Sets the TLS credentials for the client under construction using the data from the given PKCS#12 input stream.
     * Clients constructed with TLS credentials will use TLS-based authentication when sending push notifications. The
     * PKCS#12 data <em>must</em> contain a certificate/private key pair.</p>
     *
     * <p>Clients may not have both TLS credentials and a signing key.</p>
     *
     * @param p12InputStream an input stream to a PKCS#12-formatted file containing the certificate and private key to
     * be used to identify the client to the APNs server
     * @param p12Password the password to be used to decrypt the contents of the given PKCS#12 file; passwords may be
     * blank (i.e. {@code ""}), but must not be {@code null}
     *
     * @throws SSLException if the given PKCS#12 file could not be loaded or if any other SSL-related problem arises
     * when constructing the context
     * @throws IOException if any IO problem occurred while attempting to read the given PKCS#12 input stream
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder setClientCredentials(final InputStream p12InputStream, final String p12Password) throws SSLException, IOException {
        final X509Certificate x509Certificate;
        final PrivateKey privateKey;

        try {
            final KeyStore.PrivateKeyEntry privateKeyEntry = P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, p12Password);

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
     * <p>Sets the TLS credentials for the client under construction. Clients constructed with TLS credentials will use
     * TLS-based authentication when sending push notifications.</p>
     *
     * <p>Clients may not have both TLS credentials and a signing key.</p>
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
    public ApnsClientBuilder setClientCredentials(final X509Certificate clientCertificate, final PrivateKey privateKey, final String privateKeyPassword) {
        this.clientCertificate = clientCertificate;
        this.privateKey = privateKey;
        this.privateKeyPassword = privateKeyPassword;

        return this;
    }

    /**
     * <p>Sets the signing key for the client under construction. Clients constructed with a signing key will use
     * token-based authentication when sending push notifications.</p>
     *
     * <p>Clients may not have both a signing key and TLS credentials.</p>
     *
     * @param signingKey the signing key to be used by the client under construction
     *
     * @return a reference to this builder
     *
     * @see ApnsSigningKey#loadFromPkcs8File(File, String, String)
     * @see ApnsSigningKey#loadFromInputStream(InputStream, String, String)
     *
     * @since 0.10
     */
    public ApnsClientBuilder setSigningKey(final ApnsSigningKey signingKey) {
        this.signingKey = signingKey;

        return this;
    }

    /**
     * <p>Sets the trusted certificate chain for the client under construction using the contents of the given PEM
     * file. If not set (or {@code null}), the client will use the JVM's default trust manager.</p>
     *
     * <p>Callers will generally not need to set a trusted server certificate chain in normal operation, but may wish
     * to do so for <a href="https://www.owasp.org/index.php/Certificate_and_Public_Key_Pinning">certificate pinning</a>
     * or connecting to a mock server for integration testing or benchmarking.</p>
     *
     * @param certificatePemFile a PEM file containing one or more trusted certificates
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder setTrustedServerCertificateChain(final File certificatePemFile) {
        this.trustedServerCertificatePemFile = certificatePemFile;
        this.trustedServerCertificateInputStream = null;
        this.trustedServerCertificates = null;

        return this;
    }

    /**
     * <p>Sets the trusted certificate chain for the client under construction using the contents of the given PEM
     * input stream. If not set (or {@code null}), the client will use the JVM's default trust manager.</p>
     *
     * <p>Callers will generally not need to set a trusted server certificate chain in normal operation, but may wish
     * to do so for <a href="https://www.owasp.org/index.php/Certificate_and_Public_Key_Pinning">certificate pinning</a>
     * or connecting to a mock server for integration testing or benchmarking.</p>
     *
     * @param certificateInputStream an input stream to PEM-formatted data containing one or more trusted certificates
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder setTrustedServerCertificateChain(final InputStream certificateInputStream) {
        this.trustedServerCertificatePemFile = null;
        this.trustedServerCertificateInputStream = certificateInputStream;
        this.trustedServerCertificates = null;

        return this;
    }

    /**
     * <p>Sets the trusted certificate chain for the client under construction. If not set (or {@code null}), the
     * client will use the JVM's default trust manager.</p>
     *
     * <p>Callers will generally not need to set a trusted server certificate chain in normal operation, but may wish
     * to do so for <a href="https://www.owasp.org/index.php/Certificate_and_Public_Key_Pinning">certificate pinning</a>
     * or connecting to a mock server for integration testing or benchmarking.</p>
     *
     * @param certificates one or more trusted certificates
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder setTrustedServerCertificateChain(final X509Certificate... certificates) {
        this.trustedServerCertificatePemFile = null;
        this.trustedServerCertificateInputStream = null;
        this.trustedServerCertificates = certificates;

        return this;
    }

    /**
     * <p>Sets the event loop group to be used by the client under construction. If not set (or if {@code null}), the
     * client will create and manage its own event loop group.</p>
     *
     * <p>Generally speaking, callers don't need to set event loop groups for clients, but it may be useful to specify
     * an event loop group under certain circumstances. In particular, specifying an event loop group that is shared
     * among multiple {@code ApnsClient} instances can keep thread counts manageable. Regardless of the number of
     * concurrent {@code ApnsClient} instances, callers may also wish to specify an event loop group to take advantage
     * of certain platform-specific optimizations (e.g. {@code epoll} or {@code KQueue} event loop groups).</p>
     *
     * @param eventLoopGroup the event loop group to use for this client, or {@code null} to let the client manage its
     * own event loop group
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder setEventLoopGroup(final EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        return this;
    }

    /**
     * Sets the maximum number of concurrent connections the client under construction may attempt to maintain to the
     * APNs server. By default, clients will attempt to maintain a single connection to the APNs server.
     *
     * @param concurrentConnections the maximum number of concurrent connections the client under construction may
     * attempt to maintain
     *
     * @return a reference to this builder
     *
     * @since 0.11
     */
    public ApnsClientBuilder setConcurrentConnections(final int concurrentConnections) {
        this.concurrentConnections = concurrentConnections;
        return this;
    }

    /**
     * Sets the metrics listener for the client under construction. Metrics listeners gather information that describes
     * the performance and behavior of a client, and are completely optional.
     *
     * @param metricsListener the metrics listener for the client under construction, or {@code null} if this client
     * should not report metrics to a listener
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder setMetricsListener(final ApnsClientMetricsListener metricsListener) {
        this.metricsListener = metricsListener;
        return this;
    }

    /**
     * Sets the proxy handler factory to be used to construct proxy handlers when establishing a new connection to the
     * APNs gateway. A client's proxy handler factory may be {@code null}, in which case the client will connect to the
     * gateway directly and will not use a proxy. By default, clients will not use a proxy.
     *
     * @param proxyHandlerFactory the proxy handler factory to be used to construct proxy handlers, or {@code null} if
     * this client should not use a proxy
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder setProxyHandlerFactory(final ProxyHandlerFactory proxyHandlerFactory) {
        this.proxyHandlerFactory = proxyHandlerFactory;
        return this;
    }

    /**
     * Sets the maximum amount of time, in milliseconds, that the client under construction will wait to establish a
     * connection with the APNs server before the connection attempt is considered a failure.
     *
     * @param connectionTimeout the maximum amount of time to wait for a connection attempt to complete
     * @param timeoutUnit the time unit for the given timeout
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder setConnectionTimeout(final long connectionTimeout, final TimeUnit timeoutUnit) {
        this.connectionTimeoutMillis = (int) timeoutUnit.toMillis(connectionTimeout);
        return this;
    }


    /**
     * Sets the amount of idle time (in milliseconds) after which the client under construction will send a PING frame
     * to the APNs server. By default, clients will send a PING frame after
     * {@value com.turo.pushy.apns.ApnsClientBuilder#DEFAULT_PING_IDLE_TIME_MILLIS} milliseconds of inactivity.
     *
     * @param pingInterval the amount of idle time after which the client will send a PING frame
     * @param pingIntervalUnit the time unit for the given idle time
     *
     * @return a reference to this builder
     *
     * @since 0.10
     */
    public ApnsClientBuilder setIdlePingInterval(final long pingInterval, final TimeUnit pingIntervalUnit) {
        this.idlePingIntervalMillis = pingIntervalUnit.toMillis(pingInterval);
        return this;
    }

    /**
     * Sets the amount of time clients should wait for in-progress requests to complete before closing a connection
     * during a graceful shutdown.
     *
     * @param gracefulShutdownTimeout the amount of time to wait for in-progress requests to complete before closing a
     * connection
     * @param timeoutUnit the time unit for the given timeout
     *
     * @return a reference to this builder
     *
     * @see ApnsClient#close()
     *
     * @since 0.8
     */
    public ApnsClientBuilder setGracefulShutdownTimeout(final long gracefulShutdownTimeout, final TimeUnit timeoutUnit) {
        this.gracefulShutdownTimeoutMillis = timeoutUnit.toMillis(gracefulShutdownTimeout);
        return this;
    }

    /**
     * Constructs a new {@link ApnsClient} with the previously-set configuration.
     *
     * @return a new ApnsClient instance with the previously-set configuration
     *
     * @throws SSLException if an SSL context could not be created for the new client for any reason
     *
     * @since 0.8
     */
    public ApnsClient build() throws SSLException {
        if (this.apnsServerAddress == null) {
            throw new IllegalStateException("No APNs server address specified.");
        }

        if (this.clientCertificate == null && this.privateKey == null && this.signingKey == null) {
            throw new IllegalStateException("No client credentials specified; either TLS credentials (a " +
                    "certificate/private key) or an APNs signing key must be provided before building a client.");
        } else if ((this.clientCertificate != null || this.privateKey != null) && this.signingKey != null) {
            throw new IllegalStateException("Clients may not have both a signing key and TLS credentials.");
        }

        final SslContext sslContext;
        {
            final SslProvider sslProvider = SslUtil.getSslProvider();

            final SslContextBuilder sslContextBuilder = SslContextBuilder.forClient()
                    .sslProvider(sslProvider)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .applicationProtocolConfig(
                            new ApplicationProtocolConfig(Protocol.ALPN,
                                    SelectorFailureBehavior.NO_ADVERTISE,
                                    SelectedListenerFailureBehavior.ACCEPT,
                                    ApplicationProtocolNames.HTTP_2));

            if (this.clientCertificate != null && this.privateKey != null) {
                sslContextBuilder.keyManager(this.privateKey, this.privateKeyPassword, this.clientCertificate);
            }

            if (this.trustedServerCertificatePemFile != null) {
                sslContextBuilder.trustManager(this.trustedServerCertificatePemFile);
            } else if (this.trustedServerCertificateInputStream != null) {
                sslContextBuilder.trustManager(this.trustedServerCertificateInputStream);
            } else if (this.trustedServerCertificates != null) {
                sslContextBuilder.trustManager(this.trustedServerCertificates);
            }

            sslContext = sslContextBuilder.build();
        }

        return new ApnsClient(this.apnsServerAddress, sslContext, this.signingKey, this.proxyHandlerFactory,
                this.connectionTimeoutMillis, this.idlePingIntervalMillis, this.gracefulShutdownTimeoutMillis,
                this.concurrentConnections, this.metricsListener, this.eventLoopGroup);
    }
}
