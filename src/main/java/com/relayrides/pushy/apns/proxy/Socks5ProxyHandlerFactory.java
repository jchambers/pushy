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

package com.relayrides.pushy.apns.proxy;

import java.net.SocketAddress;

import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;

/**
 * A concrete {@link ProxyHandlerFactory} implementation that creates {@link Socks5ProxyHandler} instances.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.6
 */
public class Socks5ProxyHandlerFactory implements ProxyHandlerFactory {

    private final SocketAddress proxyAddress;

    private final String username;
    private final String password;

    /**
     * Creates a new proxy handler factory that will create SOCKS5 proxy handlers that use the proxy at the given
     * address and that will not perform authentication.
     *
     * @param proxyAddress the address of the SOCKS5 proxy server
     *
     * @since 0.6
     */
    public Socks5ProxyHandlerFactory(final SocketAddress proxyAddress) {
        this(proxyAddress, null, null);
    }

    /**
     * Creates a new proxy handler factory that will create SOCKS5 proxy handlers that use the proxy at the given
     * address and that will authenticate with the given username and password.
     *
     * @param proxyAddress the address of the SOCKS5 proxy server
     * @param username the username to use when connecting to the given proxy server
     * @param password the password to use when connecting to the given proxy server
     *
     * @since 0.6
     */
    public Socks5ProxyHandlerFactory(final SocketAddress proxyAddress, final String username, final String password) {
        this.proxyAddress = proxyAddress;

        this.username = username;
        this.password = password;
    }

    /*
     * (non-Javadoc)
     * @see com.relayrides.pushy.apns.proxy.ProxyHandlerFactory#createProxyHandler()
     */
    @Override
    public ProxyHandler createProxyHandler() {
        return new Socks5ProxyHandler(this.proxyAddress, this.username, this.password);
    }
}
