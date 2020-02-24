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

import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.List;

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

    private static final String PROXY_USERNAME_PROPERTY_KEY = "http.proxyUser";
    private static final String PROXY_PASSWORD_PROPERTY_KEY = "http.proxyPassword";

    /**
     * Constructs an {@code HttpProxyHandlerFactory} that uses the HTTP proxy (if any) specified in Java's standard
     * <a href="https://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html#Proxies">proxy system
     * properties</a>.
     *
     * @param apnsHost the APNs host for which to find proxy settings
     * @return an HTTP proxy factory if a proxy is configured for the given APNs host, or {@code null} if no proxy is
     * configured for the given host
     * @throws URISyntaxException if {@code apnsHost} is malformed in some way
     *
     * @see <a href="https://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html#Proxies">Java™ Platform, Standard Edition 7 - Networking Properties - Proxies</a>
     * @since 0.13.11
     */
    public static HttpProxyHandlerFactory fromSystemProxies(final String apnsHost) throws URISyntaxException {

        final SocketAddress proxyAddress = getProxyAddressForUri(new URI("https", apnsHost, null, null));

        return (proxyAddress != null)
                ? new HttpProxyHandlerFactory(proxyAddress, System.getProperty(PROXY_USERNAME_PROPERTY_KEY), System.getProperty(PROXY_PASSWORD_PROPERTY_KEY))
                : null;
    }

    /**
     * Returns the {@link SocketAddress} for any system-wide HTTP {@link Proxy} for a given {@link URI}.
     *
     * @param uri the URI for which to find a system-wide proxy
     * @return the {@link SocketAddress} of the first HTTP {@link Proxy} found, or {@code null} if there were none
     * @since 0.13.11
     */
    private static SocketAddress getProxyAddressForUri(final URI uri) {
        final ProxySelector defaultProxySelector = ProxySelector.getDefault();
        final List<Proxy> proxiesForUri = defaultProxySelector.select(uri);

        log.debug("Proxies for \"{}\": {}", uri, proxiesForUri);

        for (final java.net.Proxy proxy : proxiesForUri) {
            if (proxy.type() == Proxy.Type.HTTP) {
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

    // Visible for testing
    String getUsername() {
        return this.username;
    }

    // Visible for testing
    String getPassword() {
        return this.password;
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
