/*
 * Copyright (c) 2013-2018 Turo
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
import io.netty.handler.ssl.SslContext;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * <p>A {@code BenchmarkApnsServerBuilder} constructs new {@link BenchmarkApnsServer} instances. Callers must supply
 * server credentials via one of the {@code setServerCredentials} methods prior to constructing a new server with the
 * {@link BenchmarkApnsServerBuilder#build()} method; all other settings are optional.</p>
 *
 * <p>Server builders may be reused to generate multiple servers, and their settings may be changed from one server to
 * the next.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.13.0
 */
public class BenchmarkApnsServerBuilder extends BaseHttp2ServerBuilder<BenchmarkApnsServer> {

    @Override
    public BenchmarkApnsServerBuilder setServerCredentials(final File certificatePemFile, final File privateKeyPkcs8File, final String privateKeyPassword) {
        super.setServerCredentials(certificatePemFile, privateKeyPkcs8File, privateKeyPassword);
        return this;
    }

    @Override
    public BenchmarkApnsServerBuilder setServerCredentials(final InputStream certificatePemInputStream, final InputStream privateKeyPkcs8InputStream, final String privateKeyPassword) {
        super.setServerCredentials(certificatePemInputStream, privateKeyPkcs8InputStream, privateKeyPassword);
        return this;
    }

    @Override
    public BenchmarkApnsServerBuilder setServerCredentials(final X509Certificate[] certificates, final PrivateKey privateKey, final String privateKeyPassword) {
        super.setServerCredentials(certificates, privateKey, privateKeyPassword);
        return this;
    }

    @Override
    public BenchmarkApnsServerBuilder setTrustedClientCertificateChain(final File certificatePemFile) {
        super.setTrustedClientCertificateChain(certificatePemFile);
        return this;
    }

    @Override
    public BenchmarkApnsServerBuilder setTrustedClientCertificateChain(final InputStream certificateInputStream) {
        super.setTrustedClientCertificateChain(certificateInputStream);
        return this;
    }

    @Override
    public BenchmarkApnsServerBuilder setTrustedServerCertificateChain(final X509Certificate... certificates) {
        super.setTrustedServerCertificateChain(certificates);
        return this;
    }

    @Override
    public BenchmarkApnsServerBuilder setEventLoopGroup(final EventLoopGroup eventLoopGroup) {
        super.setEventLoopGroup(eventLoopGroup);
        return this;
    }

    @Override
    public BenchmarkApnsServerBuilder setMaxConcurrentStreams(final int maxConcurrentStreams) {
        super.setMaxConcurrentStreams(maxConcurrentStreams);
        return this;
    }

    @Override
    public BenchmarkApnsServer build() throws SSLException {
        return super.build();
    }

    @Override
    protected BenchmarkApnsServer constructServer(final SslContext sslContext) {
        return new BenchmarkApnsServer(sslContext, this.eventLoopGroup, this.maxConcurrentStreams);
    }
}
