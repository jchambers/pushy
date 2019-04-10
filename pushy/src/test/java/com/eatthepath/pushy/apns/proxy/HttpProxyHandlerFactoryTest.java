package com.eatthepath.pushy.apns.proxy;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.eatthepath.pushy.apns.ApnsClientBuilder;


public class HttpProxyHandlerFactoryTest {

    private static class FixedProxiesSelector extends ProxySelector {

        private final List<Proxy> availableProxies;

        public FixedProxiesSelector(final Proxy... availableProxies) {
            this.availableProxies = Arrays.asList(availableProxies);
        }

        @Override
        public List<Proxy> select(URI uri) {
            return availableProxies;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // Not needed for this test.
        }
    }

    private static class SingleHostHttpProxySelector extends ProxySelector {

        private final String proxiedHost;

        public SingleHostHttpProxySelector(final String proxiedHost) {
            this.proxiedHost = proxiedHost;
        }

        @Override
        public List<Proxy> select(URI uri) {
            Proxy proxy;

            if (uri.getHost().equals(proxiedHost)) {
                proxy = DUMMY_HTTP_PROXY;
            } else {
                proxy = Proxy.NO_PROXY;
            }

            return Arrays.asList(proxy);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // Not needed for this test.
        }
    }

    private static final Proxy DUMMY_HTTP_PROXY = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("httpproxy", 123));
    private static final Proxy DUMMY_SOCKS_PROXY = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("socksproxy", 456));

    private static ProxySelector defaultProxySelector;

    @BeforeClass
    public static void setUpClass() {
        defaultProxySelector = ProxySelector.getDefault();
    }

    @AfterClass
    public static void tearDownClass() {
        ProxySelector.setDefault(defaultProxySelector);
    }

    @Test
    public void testNoProxy() throws URISyntaxException {

        ProxySelector.setDefault(new FixedProxiesSelector(Proxy.NO_PROXY));

        assertNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.PRODUCTION_APNS_HOST));
        assertNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));
    }

    @Test
    public void testHasHttpProxy() throws URISyntaxException {

        ProxySelector.setDefault(new FixedProxiesSelector(DUMMY_HTTP_PROXY));

        assertNotNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.PRODUCTION_APNS_HOST));
        assertNotNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));
    }

    @Test
    public void testHasSocksProxy() throws URISyntaxException {

        ProxySelector.setDefault(new FixedProxiesSelector(DUMMY_SOCKS_PROXY));

        assertNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.PRODUCTION_APNS_HOST));
        assertNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));
    }

    @Test
    public void testHasSocksAndHttpProxies() throws URISyntaxException {

        ProxySelector.setDefault(new FixedProxiesSelector(DUMMY_HTTP_PROXY, DUMMY_SOCKS_PROXY));

        assertNotNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.PRODUCTION_APNS_HOST));
        assertNotNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));
    }

    @Test
    public void testHasHttpProxyForApnsProductionOnly() throws URISyntaxException {

        ProxySelector.setDefault(new SingleHostHttpProxySelector(ApnsClientBuilder.PRODUCTION_APNS_HOST));

        assertNotNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.PRODUCTION_APNS_HOST));
        assertNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));
    }

    @Test
    public void testHasHttpProxyForApnsDevelopmentOnly() throws URISyntaxException {

        ProxySelector.setDefault(new SingleHostHttpProxySelector(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));

        assertNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.PRODUCTION_APNS_HOST));
        assertNotNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));
    }
}
