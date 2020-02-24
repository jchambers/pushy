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

package com.turo.pushy.apns.proxy;

import java.net.SocketAddress;

import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;

/**
 * A concrete {@link ProxyHandlerFactory} implementation that creates {@link HttpProxyHandler} instances.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.6
 */
public class HttpProxyHandlerFactory implements ProxyHandlerFactory {

    private final SocketAddress proxyAddress;

    private final String username;
    private final String password;

    /**
     * Creates a new proxy handler factory that will create HTTP proxy handlers that use the proxy at the given
     * address and that will not perform authentication.
     *
     * @param proxyAddress the address of the HTTP proxy server
     *
     * @since 0.6
     */
    public HttpProxyHandlerFactory(final SocketAddress proxyAddress) {
        this(proxyAddress, null, null);
    }

    /**
     * Creates a new proxy handler factory that will create HTTP proxy handlers that use the proxy at the given
     * address and that will authenticate with the given username and password.
     *
     * @param proxyAddress the address of the HTTP proxy server
     * @param username the username to use when connecting to the given proxy server
     * @param password the password to use when connecting to the given proxy server
     *
     * @since 0.6
     */
    public HttpProxyHandlerFactory(final SocketAddress proxyAddress, final String username, final String password) {
        this.proxyAddress = proxyAddress;

        this.username = username;
        this.password = password;
    }

    /*
     * (non-Javadoc)
     * @see ProxyHandlerFactory#createProxyHandler()
     */
    @Override
    public ProxyHandler createProxyHandler() {
        final HttpProxyHandler handler;

        // For reasons that are not immediately clear, HttpProxyHandler doesn't allow null usernames/passwords if
        // specified. If we want them to be null, we have to use the constructor that doesn't take a username/password
        // at all.
        if (this.username != null && this.password != null) {
            handler = new HttpProxyHandler(this.proxyAddress, this.username, this.password);
        } else {
            handler = new HttpProxyHandler(this.proxyAddress);
        }

        return handler;
    }
}
