/*
 * Copyright (c) 2021 Jon Chambers
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

package com.eatthepath.pushy.apns;

import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.proxy.ProxyHandlerFactory;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.ssl.SslContext;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * A simple carrier for APNs client configuration options.
 */
class ApnsClientConfiguration {

    private static final Duration DEFAULT_TOKEN_EXPIRATION = Duration.ofMinutes(50);

    private final InetSocketAddress apnsServerAddress;
    private final SslContext sslContext;
    private final boolean hostnameVerificationEnabled;
    private final ApnsSigningKey signingKey;
    private final Duration tokenExpiration;
    private final ProxyHandlerFactory proxyHandlerFactory;
    private final Duration connectionTimeout;
    private final Duration closeAfterIdleDuration;
    private final Duration gracefulShutdownTimeout;
    private final int concurrentConnections;
    private final ApnsClientMetricsListener metricsListener;
    private final Http2FrameLogger frameLogger;

    public ApnsClientConfiguration(final InetSocketAddress apnsServerAddress,
                                   final SslContext sslContext,
                                   final boolean hostnameVerificationEnabled,
                                   final ApnsSigningKey signingKey,
                                   final Duration tokenExpiration,
                                   final ProxyHandlerFactory proxyHandlerFactory,
                                   final Duration connectionTimeout,
                                   final Duration closeAfterIdleDuration,
                                   final Duration gracefulShutdownTimeout,
                                   final int concurrentConnections,
                                   final ApnsClientMetricsListener metricsListener,
                                   final Http2FrameLogger frameLogger) {

        this.apnsServerAddress = Objects.requireNonNull(apnsServerAddress);
        this.sslContext = Objects.requireNonNull(sslContext);
        this.hostnameVerificationEnabled = hostnameVerificationEnabled;
        this.signingKey = signingKey;
        this.tokenExpiration = tokenExpiration != null ? tokenExpiration : DEFAULT_TOKEN_EXPIRATION;
        this.proxyHandlerFactory = proxyHandlerFactory;
        this.connectionTimeout = connectionTimeout;
        this.closeAfterIdleDuration = closeAfterIdleDuration;
        this.gracefulShutdownTimeout = gracefulShutdownTimeout;
        this.concurrentConnections = concurrentConnections;
        this.metricsListener = metricsListener;
        this.frameLogger = frameLogger;
    }

    public InetSocketAddress getApnsServerAddress() {
        return apnsServerAddress;
    }

    public SslContext getSslContext() {
        return sslContext;
    }

    public boolean isHostnameVerificationEnabled() {
        return hostnameVerificationEnabled;
    }

    public Optional<ApnsSigningKey> getSigningKey() {
        return Optional.ofNullable(signingKey);
    }

    public Duration getTokenExpiration() {
        return tokenExpiration;
    }

    public Optional<ProxyHandlerFactory> getProxyHandlerFactory() {
        return Optional.ofNullable(proxyHandlerFactory);
    }

    public Optional<Duration> getConnectionTimeout() {
        return Optional.ofNullable(connectionTimeout);
    }

    public Duration getCloseAfterIdleDuration() {
        return closeAfterIdleDuration;
    }

    public Optional<Duration> getGracefulShutdownTimeout() {
        return Optional.ofNullable(gracefulShutdownTimeout);
    }

    public int getConcurrentConnections() {
        return concurrentConnections;
    }

    public Optional<ApnsClientMetricsListener> getMetricsListener() {
        return Optional.ofNullable(metricsListener);
    }

    public Optional<Http2FrameLogger> getFrameLogger() {
        return Optional.ofNullable(frameLogger);
    }
}
