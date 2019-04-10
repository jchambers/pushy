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

package com.eatthepath.pushy.apns.proxy;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(HttpProxyHandlerFactory.class);

    private final SocketAddress proxyAddress;

    private final String username;
    private final String password;

    /**
     * Checks the system default proxies to determine if an {@link HttpProxyHandlerFactory}
     * is required for the given host or not, returning an instance or null for each case.
     *
     * @param apnsHost the APNs host to check e.g. "api.push.apple.com".
     * @return an {@link HttpProxyHandlerFactory} if one is required, or {@code null} if not.
     * @throws URISyntaxException if <code>apnsHost</code> is malformed in some way.
     *
     * @see <a href="https://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html#Proxies">The Java documentation on proxies.</a>
     * @since 0.14
     */
    public static HttpProxyHandlerFactory fromSystemProxies(final String apnsHost)
            throws URISyntaxException {

        final URI hostUri = new URI("https", apnsHost, null, null);
        final SocketAddress proxyAddress = getProxyAddressForUri(hostUri, Proxy.Type.HTTP);

        return (proxyAddress != null) ? new HttpProxyHandlerFactory(proxyAddress) : null;
    }

    /**
     * Returns the {@link SocketAddress} for any system-wide {@link Proxy} of a given {@link Proxy.Type} for a given {@link URI}.
     *
     * @param uri the URI to find any system proxy for.
     * @param proxyType the types of proxy to find for the URI.
     * @return the {@link SocketAddress} of any <code>Proxy</code> found, or <code>null</code> if there were none.
     * @since 0.14
     */
    private static SocketAddress getProxyAddressForUri(final URI uri, final Proxy.Type proxyType) {
        final ProxySelector defaultProxySelector = ProxySelector.getDefault();
        final List<Proxy> proxiesForUri = defaultProxySelector.select(uri);
        log.debug("Proxies for URI \"{}\" were {}", uri, proxiesForUri);

        for (java.net.Proxy proxy : proxiesForUri) {
            if (proxy.type() == proxyType) {
                InetSocketAddress proxyAddress = (InetSocketAddress) proxy.address();
                if (proxyAddress.isUnresolved()) {
                    proxyAddress = new InetSocketAddress(proxyAddress.getHostString(), proxyAddress.getPort());
                }
                return proxyAddress;
            }
        }

        return null;
    }

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

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "HttpProxyHandlerFactory {" +
                "proxyAddress=" + proxyAddress +
                ", username=" + username +
                ", password=*****"
                + "}";
    }
}
