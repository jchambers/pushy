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
import io.netty.handler.ssl.SslContext;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * <p>A {@code MockApnsServerBuilder} constructs new {@link MockApnsServer} instances. Callers must supply server
 * credentials via one of the {@code setServerCredentials} methods and supply a {@link PushNotificationHandlerFactory}
 * prior to constructing a new server with the {@link MockApnsServerBuilder#build()} method; all other settings are
 * optional.</p>
 *
 * <p>Server builders may be reused to generate multiple servers, and their settings may be changed from one server to
 * the next.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.8
 */
public class MockApnsServerBuilder extends BaseHttp2ServerBuilder<MockApnsServer> {

    private PushNotificationHandlerFactory handlerFactory;
    private MockApnsServerListener listener;

    @Override
    public MockApnsServerBuilder setServerCredentials(final File certificatePemFile, final File privateKeyPkcs8File, final String privateKeyPassword) {
        super.setServerCredentials(certificatePemFile, privateKeyPkcs8File, privateKeyPassword);
        return this;
    }

    @Override
    public MockApnsServerBuilder setServerCredentials(final InputStream certificatePemInputStream, final InputStream privateKeyPkcs8InputStream, final String privateKeyPassword) {
        super.setServerCredentials(certificatePemInputStream, privateKeyPkcs8InputStream, privateKeyPassword);
        return this;
    }

    @Override
    public MockApnsServerBuilder setServerCredentials(final X509Certificate[] certificates, final PrivateKey privateKey, final String privateKeyPassword) {
        super.setServerCredentials(certificates, privateKey, privateKeyPassword);
        return this;
    }

    @Override
    public MockApnsServerBuilder setTrustedClientCertificateChain(final File certificatePemFile) {
        super.setTrustedClientCertificateChain(certificatePemFile);
        return this;
    }

    @Override
    public MockApnsServerBuilder setTrustedClientCertificateChain(final InputStream certificateInputStream) {
        super.setTrustedClientCertificateChain(certificateInputStream);
        return this;
    }

    @Override
    public MockApnsServerBuilder setTrustedServerCertificateChain(final X509Certificate... certificates) {
        super.setTrustedServerCertificateChain(certificates);
        return this;
    }

    @Override
    public MockApnsServerBuilder setEventLoopGroup(final EventLoopGroup eventLoopGroup) {
        super.setEventLoopGroup(eventLoopGroup);
        return this;
    }

    @Override
    public MockApnsServerBuilder setMaxConcurrentStreams(final int maxConcurrentStreams) {
        super.setMaxConcurrentStreams(maxConcurrentStreams);
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
     * Sets the listener to be notified when notifications are accepted or rejected by the server under construction. If
     * not set or if {@code null}, the server will not notify a listener when notifications are accepted or rejected.
     *
     * @param listener the listener to be used by the server under construction
     *
     * @return a reference to this builder
     *
     * @since 0.12
     */
    public MockApnsServerBuilder setListener(final MockApnsServerListener listener) {
        this.listener = listener;
        return this;
    }

    @Override
    public MockApnsServer build() throws SSLException {
        return super.build();
    }

    @Override
    protected MockApnsServer constructServer(final SslContext sslContext) {
        if (this.handlerFactory == null) {
            throw new IllegalStateException("Must provide a push notification handler factory before building a mock server.");
        }

        return new MockApnsServer(sslContext, this.eventLoopGroup, this.handlerFactory, this.listener, this.maxConcurrentStreams);
    }
}
