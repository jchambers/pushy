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
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.relayrides.pushy.apns.proxy.ProxyHandlerFactory;

import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;

/**
 * An {@code ApnsClientBuilder} constructs new {@link ApnsClient} instances. All settings are optional. Client builders
 * may be reused to generate multiple clients, and their settings may be changed from one client to the next.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public class ApnsClientBuilder {
    private File trustedServerCertificatePemFile;
    private InputStream trustedServerCertificateInputStream;
    private X509Certificate[] trustedServerCertificates;

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
     * of certain platform-specific optimizations (e.g. epoll event loop groups).</p>
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
        this.connectionTimeout = connectionTimeout;
        this.connectionTimeoutUnit = timeoutUnit;

        return this;
    }

    /**
     * <p>Sets the write timeout for the client to build. If an attempt to send a notification to the APNs server takes
     * longer than the given timeout, the connection will be closed (and automatically reconnected later). Note that
     * write timeouts refer to the amount of time taken to <em>send</em> a notification to the server, and not the time
     * taken by the server to process and respond to a notification.</p>
     *
     * <p>By default, clients have a write timeout of
     * {@value com.relayrides.pushy.apns.ApnsClient#DEFAULT_WRITE_TIMEOUT_MILLIS} milliseconds.</p>
     *
     * @param writeTimeout the write timeout for the client under construction
     * @param timeoutUnit the time unit for the given timeout
     *
     * @return a reference to this builder
     *
     * @since 0.8
     */
    public ApnsClientBuilder setWriteTimeout(final long writeTimeout, final TimeUnit timeoutUnit) {
        this.writeTimeout = writeTimeout;
        this.writeTimeoutUnit = timeoutUnit;

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
     * @see ApnsClient#disconnect()
     *
     * @since 0.8
     */
    public ApnsClientBuilder setGracefulShutdownTimeout(final long gracefulShutdownTimeout, final TimeUnit timeoutUnit) {
        this.gracefulShutdownTimeout = gracefulShutdownTimeout;
        this.gracefulShutdownTimeoutUnit = timeoutUnit;

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
                    .applicationProtocolConfig(
                            new ApplicationProtocolConfig(Protocol.ALPN,
                                    SelectorFailureBehavior.NO_ADVERTISE,
                                    SelectedListenerFailureBehavior.ACCEPT,
                                    ApplicationProtocolNames.HTTP_2));

            if (this.trustedServerCertificatePemFile != null) {
                sslContextBuilder.trustManager(this.trustedServerCertificatePemFile);
            } else if (this.trustedServerCertificateInputStream != null) {
                sslContextBuilder.trustManager(this.trustedServerCertificateInputStream);
            } else if (this.trustedServerCertificates != null) {
                sslContextBuilder.trustManager(this.trustedServerCertificates);
            }

            sslContext = sslContextBuilder.build();
        }

        final ApnsClient apnsClient = new ApnsClient(sslContext, this.eventLoopGroup);

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
