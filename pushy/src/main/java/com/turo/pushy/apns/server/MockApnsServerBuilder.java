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

package com.turo.pushy.apns.server;

import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.util.ReferenceCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * <p>A {@code MockApnsServerBuilder} constructs new {@link MockApnsServer} instances. Callers must supply server
 * credentials via one of the {@code setServerCredentials} methods prior to constructing a new server with the
 * {@link MockApnsServerBuilder#build()} method; all other settings are optional.</p>
 *
 * <p>Server builders may be reused to generate multiple servers, and their settings may be changed from one server to
 * the next.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.8
 */
public class MockApnsServerBuilder {
    private X509Certificate[] certificateChain;
    private PrivateKey privateKey;

    private File certificateChainPemFile;
    private File privateKeyPkcs8File;

    private InputStream certificateChainInputStream;
    private InputStream privateKeyPkcs8InputStream;

    private String privateKeyPassword;

    private File trustedClientCertificatePemFile;
    private InputStream trustedClientCertificateInputStream;
    private X509Certificate[] trustedClientCertificates;

    private PushNotificationHandlerFactory handlerFactory;

    private MockApnsServerListener listener;

    private EventLoopGroup eventLoopGroup;

    private int maxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;

    /**
     * The default maximum number of concurrent streams for an APNs server, which matches the default limit set by the
     * real APNs server at the time of this writing.
     */
    public static final int DEFAULT_MAX_CONCURRENT_STREAMS = 1500;

    private static final Logger log = LoggerFactory.getLogger(MockApnsServerBuilder.class);

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
    public MockApnsServerBuilder setServerCredentials(final File certificatePemFile, final File privateKeyPkcs8File, final String privateKeyPassword) {
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
    public MockApnsServerBuilder setServerCredentials(final InputStream certificatePemInputStream, final InputStream privateKeyPkcs8InputStream, final String privateKeyPassword) {
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
    public MockApnsServerBuilder setServerCredentials(final X509Certificate[] certificates, final PrivateKey privateKey, final String privateKeyPassword) {
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
    public MockApnsServerBuilder setTrustedClientCertificateChain(final File certificatePemFile) {
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
    public MockApnsServerBuilder setTrustedClientCertificateChain(final InputStream certificateInputStream) {
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
    public MockApnsServerBuilder setTrustedServerCertificateChain(final X509Certificate... certificates) {
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
    public MockApnsServerBuilder setEventLoopGroup(final EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        return this;
    }

    /**
     * Sets the handler factory to be used to construct push notification handlers for the server under construction.
     * Servers require a handler factory.
     *
     * @param handlerFactory the handler factory to be used by the server under construction
     *
     * @return a reference to this builder
     *
     * @since 0.12
     */
    public MockApnsServerBuilder setHandlerFactory(final PushNotificationHandlerFactory handlerFactory) {
        this.handlerFactory = handlerFactory;
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
    public MockApnsServerBuilder setMaxConcurrentStreams(final int maxConcurrentStreams) {
        if (maxConcurrentStreams <= 0) {
            throw new IllegalArgumentException("Maximum number of concurrent streams must be positive.");
        }

        this.maxConcurrentStreams = maxConcurrentStreams;
        return this;
    }

    public MockApnsServerBuilder setListener(final MockApnsServerListener listener) {
        this.listener = listener;
        return this;
    }

    /**
     * Constructs a new {@link MockApnsServer} with the previously-set configuration.
     *
     * @return a new MockApnsServer instance with the previously-set configuration
     *
     * @throws SSLException if an SSL context could not be created for the new server for any reason
     *
     * @since 0.8
     */
    public MockApnsServer build() throws SSLException {
        if (this.handlerFactory == null) {
            throw new IllegalStateException("Must provide a push notification handler factory before building a mock server.");
        }

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
                    .clientAuth(ClientAuth.OPTIONAL)
                    .applicationProtocolConfig(new ApplicationProtocolConfig(
                            Protocol.ALPN,
                            SelectorFailureBehavior.NO_ADVERTISE,
                            SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2));

            if (this.trustedClientCertificatePemFile != null) {
                sslContextBuilder.trustManager(this.trustedClientCertificatePemFile);
            } else if (this.trustedClientCertificateInputStream != null) {
                sslContextBuilder.trustManager(this.trustedClientCertificateInputStream);
            } else if (this.trustedClientCertificates != null) {
                sslContextBuilder.trustManager(this.trustedClientCertificates);
            }

            sslContext = sslContextBuilder.build();
        }

        final MockApnsServer server = new MockApnsServer(sslContext, this.handlerFactory, this.maxConcurrentStreams, this.eventLoopGroup);

        if (sslContext instanceof ReferenceCounted) {
            ((ReferenceCounted) sslContext).release();
        }

        return server;
    }
}
