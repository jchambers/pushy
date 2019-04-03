package com.turo.pushy.apns.proxy;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to look for proxy configurations.
 *
 * @see <a href="https://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html#Proxies">The Java documentation on proxies.</a>
 * @since 0.14
 */
class ProxyLocator {

    private static final Logger log = LoggerFactory.getLogger(ProxyLocator.class);

    /**
     * Searches for any system-wide {@link Proxy} of a given {@link Proxy.Type} for a given {@link URI}.
     *
     * @param uri the URI to find any system proxy for.
     * @param proxyType the types of proxy to find for the URI.
     * @return any appropriate {@link Proxy} instance, or <code>null</code> if there were none.
     * @since 0.14
     */
    static Proxy getProxyForUri(final URI uri, final Proxy.Type proxyType) {
        ProxySelector defaultProxySelector = ProxySelector.getDefault();
        List<Proxy> proxiesForUri = defaultProxySelector.select(uri);
        log.debug("Proxies for URI \"{}\" were {}", uri, proxiesForUri);

        for (java.net.Proxy proxy : proxiesForUri) {
            if (proxy.type() == proxyType) {
                return proxy;
            }
        }

        return null;
    }
}
