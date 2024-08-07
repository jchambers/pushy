/*
 * Copyright (c) 2020 Jon Chambers
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

package com.eatthepath.pushy.apns.server;

import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.netty.util.ReferenceCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

@SuppressWarnings("UnusedReturnValue")
abstract class BaseHttp2ServerBuilder <T extends BaseHttp2Server> {

    protected X509Certificate[] certificateChain;
    protected PrivateKey privateKey;

    protected File certificateChainPemFile;
    protected File privateKeyPkcs8File;

    protected InputStream certificateChainInputStream;
    protected InputStream privateKeyPkcs8InputStream;

    protected String privateKeyPassword;

    protected File trustedClientCertificatePemFile;
    protected InputStream trustedClientCertificateInputStream;
    protected X509Certificate[] trustedClientCertificates;

    protected EventLoopGroup eventLoopGroup;

    protected int maxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;

    protected boolean useAlpn;

    /**
     * The default maximum number of concurrent streams for an APNs server, which matches the default limit set by the
     * real APNs server at the time of this writing.
     */
    public static final int DEFAULT_MAX_CONCURRENT_STREAMS = 1500;

    private static final Logger log = LoggerFactory.getLogger(BaseHttp2ServerBuilder.class);

    /**
     * <p>Sets the credentials for the server under construction using the certificates in the given PEM file and the
     * private key in the given PKCS#8 file.</p>
     *
     * @param certificatePemFile a PEM file containing the certificate chain for the server under construction
     * @param privateKeyPkcs8File a PKCS#8 file containing the private key for the server under construction
     * @param privateKeyPassword the password for the given private key, or {@code null} if the key is not
     * password-protected
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public BaseHttp2ServerBuilder<T> setServerCredentials(final File certificatePemFile, final File privateKeyPkcs8File, final String privateKeyPassword) {
        this.certificateChain = null;
        this.privateKey = null;

        this.certificateChainPemFile = certificatePemFile;
        this.privateKeyPkcs8File = privateKeyPkcs8File;

        this.certificateChainInputStream = null;
        this.privateKeyPkcs8InputStream = null;

        this.privateKeyPassword = privateKeyPassword;

        return this;
    }

    /**
     * <p>Sets the credentials for the server under construction using the certificates in the given PEM input stream
     * and the private key in the given PKCS#8 input stream.</p>
     *
     * @param certificatePemInputStream a PEM input stream containing the certificate chain for the server under
     * construction
     * @param privateKeyPkcs8InputStream a PKCS#8 input stream containing the private key for the server under
     * construction
     * @param privateKeyPassword the password for the given private key, or {@code null} if the key is not
     * password-protected
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public BaseHttp2ServerBuilder<T> setServerCredentials(final InputStream certificatePemInputStream, final InputStream privateKeyPkcs8InputStream, final String privateKeyPassword) {
        this.certificateChain = null;
        this.privateKey = null;

        this.certificateChainPemFile = null;
        this.privateKeyPkcs8File = null;

        this.certificateChainInputStream = certificatePemInputStream;
        this.privateKeyPkcs8InputStream = privateKeyPkcs8InputStream;

        this.privateKeyPassword = privateKeyPassword;

        return this;
    }

    /**
     * <p>Sets the credentials for the server under construction. This method assumes that the given private key does
     * not require a password.</p>
     *
     * @param certificates a certificate chain including the server's own certificate
     * @param privateKey the private key for the server's certificate
     *
     * @return a reference to this builder
     *
     * @since 0.16
     */
    public BaseHttp2ServerBuilder<T> setServerCredentials(final X509Certificate[] certificates, final PrivateKey privateKey) {
        return this.setServerCredentials(certificates, privateKey, null);
    }

    /**
     * <p>Sets the credentials for the server under construction.</p>
     *
     * @param certificates a certificate chain including the server's own certificate
     * @param privateKey the private key for the server's certificate
     * @param privateKeyPassword the password for the given private key, or {@code null} if the key is not
     * password-protected
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public BaseHttp2ServerBuilder<T> setServerCredentials(final X509Certificate[] certificates, final PrivateKey privateKey, final String privateKeyPassword) {
        this.certificateChain = certificates;
        this.privateKey = privateKey;

        this.certificateChainPemFile = null;
        this.privateKeyPkcs8File = null;

        this.certificateChainInputStream = null;
        this.privateKeyPkcs8InputStream = null;

        this.privateKeyPassword = privateKeyPassword;

        return this;
    }

    /**
     * <p>Sets the trusted certificate chain for the server under construction using the contents of the given PEM
     * file. If not set (or {@code null}), the server will use the JVM's default trust manager.</p>
     *
     * <p>In development environments, callers will almost always need to provide a trusted certificate chain for
     * clients (since clients in development environments will generally not present credentials recognized by the JVM's
     * default trust manager).</p>
     *
     * @param certificatePemFile a PEM file containing one or more trusted certificates
     *
     * @return a reference to this builder
     */
    public BaseHttp2ServerBuilder<T> setTrustedClientCertificateChain(final File certificatePemFile) {
        this.trustedClientCertificatePemFile = certificatePemFile;
        this.trustedClientCertificateInputStream = null;
        this.trustedClientCertificates = null;

        return this;
    }

    /**
     * <p>Sets the trusted certificate chain for the server under construction using the contents of the given PEM
     * input stream. If not set (or {@code null}), the server will use the JVM's default trust manager.</p>
     *
     * <p>In development environments, callers will almost always need to provide a trusted certificate chain for
     * clients (since clients in development environments will generally not present credentials recognized by the JVM's
     * default trust manager).</p>
     *
     * @param certificateInputStream an input stream to PEM-formatted data containing one or more trusted certificates
     *
     * @return a reference to this builder
     */
    public BaseHttp2ServerBuilder<T> setTrustedClientCertificateChain(final InputStream certificateInputStream) {
        this.trustedClientCertificatePemFile = null;
        this.trustedClientCertificateInputStream = certificateInputStream;
        this.trustedClientCertificates = null;

        return this;
    }

    /**
     * <p>Sets the trusted certificate chain for the server under construction. If not set (or {@code null}), the
     * server will use the JVM's default trust manager.</p>
     *
     * <p>In development environments, callers will almost always need to provide a trusted certificate chain for
     * clients (since clients in development environments will generally not present credentials recognized by the JVM's
     * default trust manager).</p>
     *
     * @param certificates one or more trusted certificates
     *
     * @return a reference to this builder
     */
    public BaseHttp2ServerBuilder<T> setTrustedServerCertificateChain(final X509Certificate... certificates) {
        this.trustedClientCertificatePemFile = null;
        this.trustedClientCertificateInputStream = null;
        this.trustedClientCertificates = certificates;

        return this;
    }

    /**
     * <p>Sets the event loop group to be used by the server under construction. If not set (or if {@code null}), the
     * server will create and manage its own event loop group.</p>
     *
     * @param eventLoopGroup the event loop group to use for this server, or {@code null} to let the server manage its
     * own event loop group
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public BaseHttp2ServerBuilder<T> setEventLoopGroup(final EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        return this;
    }

    /**
     * Sets the maximum number of concurrent HTTP/2 streams allowed by the server under construction. By default,
     * mock servers will have a concurrent stream limit of {@value DEFAULT_MAX_CONCURRENT_STREAMS}.
     *
     * @param maxConcurrentStreams the maximum number of concurrent HTTP/2 streams allowed by the server under
     * construction; must be positive
     *
     * @return a reference to this builder
     *
     * @since 0.12
     */
    public BaseHttp2ServerBuilder<T> setMaxConcurrentStreams(final int maxConcurrentStreams) {
        if (maxConcurrentStreams <= 0) {
            throw new IllegalArgumentException("Maximum number of concurrent streams must be positive.");
        }

        this.maxConcurrentStreams = maxConcurrentStreams;
        return this;
    }

    /**
     * <p>Sets whether the server under construction should use ALPN. By default, mock servers do not use ALPN and
     * instead require clients to use direct negotiation.</p>
     *
     * <p>Note that Pushy itself does <em>not</em> require (or even use) ALPN and always uses direct protocol
     * negotiation. ALPN is <em>only</em> useful in cases where Pushy's mock server is being used with a non-Pushy APNs
     * client.</p>
     *
     * <p>Note also that turning on ALPN support may introduce new system requirements for the mock server. Prior to
     * version 9, Java does not include support for ALPN, and so it will need to be provided by thid-party software such
     * as the Jetty ALPN agent or Netty's netty-tcnative package.</p>
     *
     * @param useAlpn {@code true} to enable ALPN support, or {@code false} to require direct protocol negotiation
     *
     * @return a reference to this builder
     *
     * @see <a href="https://github.com/jetty-project/jetty-alpn-agent">Jetty ALPN Agent</a>
     * @see <a href="https://netty.io/wiki/forked-tomcat-native.html">netty-tcnative</a>
     *
     * @since 0.13.7
     */
    public BaseHttp2ServerBuilder<T> setUseAlpn(final boolean useAlpn) {
        this.useAlpn = useAlpn;
        return this;
    }

    /**
     * Constructs a new server with the previously-set configuration.
     *
     * @return a new server instance with the previously-set configuration
     *
     * @throws SSLException if an SSL context could not be created for the new server for any reason
     *
     * @since 0.8
     */
    public T build() throws SSLException {
        final SslContext sslContext;
        {
            final SslProvider sslProvider;

            if (OpenSsl.isAvailable()) {
                log.info("Native SSL provider is available; will use native provider.");
                sslProvider = SslProvider.OPENSSL;
            } else {
                log.info("Native SSL provider not available; will use JDK SSL provider.");
                sslProvider = SslProvider.JDK;
            }

            final SslContextBuilder sslContextBuilder;

            if (this.certificateChain != null && this.privateKey != null) {
                sslContextBuilder = SslContextBuilder.forServer(this.privateKey, this.privateKeyPassword, this.certificateChain);
            } else if (this.certificateChainPemFile != null && this.privateKeyPkcs8File != null) {
                sslContextBuilder = SslContextBuilder.forServer(this.certificateChainPemFile, this.privateKeyPkcs8File, this.privateKeyPassword);
            } else if (this.certificateChainInputStream != null && this.privateKeyPkcs8InputStream != null) {
                sslContextBuilder = SslContextBuilder.forServer(this.certificateChainInputStream, this.privateKeyPkcs8InputStream, this.privateKeyPassword);
            } else {
                throw new IllegalStateException("Must specify server credentials before building a mock server.");
            }

            sslContextBuilder.sslProvider(sslProvider)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .clientAuth(ClientAuth.OPTIONAL);

            if (this.trustedClientCertificatePemFile != null) {
                sslContextBuilder.trustManager(this.trustedClientCertificatePemFile);
            } else if (this.trustedClientCertificateInputStream != null) {
                sslContextBuilder.trustManager(this.trustedClientCertificateInputStream);
            } else if (this.trustedClientCertificates != null) {
                sslContextBuilder.trustManager(this.trustedClientCertificates);
            }

            if (this.useAlpn) {
                sslContextBuilder.applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2));
            }

            sslContext = sslContextBuilder.build();
        }

        try {
            return this.constructServer(sslContext);
        } finally {
            if (sslContext instanceof ReferenceCounted) {
                ((ReferenceCounted) sslContext).release();
            }
        }
    }

    protected abstract T constructServer(final SslContext sslContext);
}
