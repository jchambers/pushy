package com.eatthepath.pushy.apns.proxy;

import com.eatthepath.pushy.apns.ApnsClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HttpProxyHandlerFactoryTest {

    private static abstract class TestProxySelectorAdapter extends ProxySelector {

        @Override
        public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
            // Not needed for this test.
        }
    }

    private static class FixedProxiesSelector extends TestProxySelectorAdapter {

        private final List<Proxy> availableProxies;

        public FixedProxiesSelector(final Proxy... availableProxies) {
            this.availableProxies = Arrays.asList(availableProxies);
        }

        @Override
        public List<Proxy> select(final URI uri) {
            return availableProxies;
        }
    }

    private static class SingleHostHttpProxySelector extends TestProxySelectorAdapter {

        private final String proxiedHost;

        public SingleHostHttpProxySelector(final String proxiedHost) {
            this.proxiedHost = proxiedHost;
        }

        @Override
        public List<Proxy> select(final URI uri) {
            return Collections.singletonList(uri.getHost().equals(proxiedHost) ? DUMMY_HTTP_PROXY : Proxy.NO_PROXY);
        }
    }

    private static final Proxy DUMMY_HTTP_PROXY = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("httpproxy", 123));
    private static final Proxy DUMMY_SOCKS_PROXY = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("socksproxy", 456));

    private static final String USERNAME_PROPERTY_KEY = "http.proxyUser";
    private static final String PASSWORD_PROPERTY_KEY = "http.proxyPassword";

    private static ProxySelector defaultProxySelector;

    @BeforeAll
    public static void setUpClass() {
        defaultProxySelector = ProxySelector.getDefault();
    }

    @AfterAll
    public static void tearDownClass() {
        ProxySelector.setDefault(defaultProxySelector);
    }

    @Test
    void testNoProxy() throws URISyntaxException {

        ProxySelector.setDefault(new FixedProxiesSelector(Proxy.NO_PROXY));

        assertNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.PRODUCTION_APNS_HOST));
        assertNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));
    }

    @Test
    void testHasHttpProxy() throws URISyntaxException {

        ProxySelector.setDefault(new FixedProxiesSelector(DUMMY_HTTP_PROXY));

        assertNotNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.PRODUCTION_APNS_HOST));
        assertNotNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));
    }

    @Test
    void testHasSocksProxy() throws URISyntaxException {

        ProxySelector.setDefault(new FixedProxiesSelector(DUMMY_SOCKS_PROXY));

        assertNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.PRODUCTION_APNS_HOST));
        assertNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));
    }

    @Test
    void testHasSocksAndHttpProxies() throws URISyntaxException {

        ProxySelector.setDefault(new FixedProxiesSelector(DUMMY_HTTP_PROXY, DUMMY_SOCKS_PROXY));

        assertNotNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.PRODUCTION_APNS_HOST));
        assertNotNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));
    }

    @Test
    void testHasHttpProxyForApnsProductionOnly() throws URISyntaxException {

        ProxySelector.setDefault(new SingleHostHttpProxySelector(ApnsClientBuilder.PRODUCTION_APNS_HOST));

        assertNotNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.PRODUCTION_APNS_HOST));
        assertNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));
    }

    @Test
    void testHasHttpProxyForApnsDevelopmentOnly() throws URISyntaxException {

        ProxySelector.setDefault(new SingleHostHttpProxySelector(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));

        assertNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.PRODUCTION_APNS_HOST));
        assertNotNull(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));
    }

    @Test
    void testProxyWithCredentials() throws URISyntaxException {
        final String originalUsername = System.getProperty(USERNAME_PROPERTY_KEY);
        final String originalPassword = System.getProperty(PASSWORD_PROPERTY_KEY);

        try {
            ProxySelector.setDefault(new SingleHostHttpProxySelector(ApnsClientBuilder.DEVELOPMENT_APNS_HOST));

            {
                System.clearProperty(USERNAME_PROPERTY_KEY);
                System.clearProperty(PASSWORD_PROPERTY_KEY);

                final HttpProxyHandlerFactory noCredentialProxyHandlerFactory =
                        HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST);

                assertNotNull(noCredentialProxyHandlerFactory);
                assertNull(noCredentialProxyHandlerFactory.getUsername());
                assertNull(noCredentialProxyHandlerFactory.getPassword());
            }

            {
                final String username = "username";
                final String password = "password";

                System.setProperty(USERNAME_PROPERTY_KEY, username);
                System.setProperty(PASSWORD_PROPERTY_KEY, password);

                final HttpProxyHandlerFactory credentialedProxyHandlerFactory =
                        HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST);

                assertNotNull(credentialedProxyHandlerFactory);
                assertEquals(username, credentialedProxyHandlerFactory.getUsername());
                assertEquals(password, credentialedProxyHandlerFactory.getPassword());
            }
        } finally {
            if (originalUsername == null) {
                System.clearProperty(USERNAME_PROPERTY_KEY);
            } else {
                System.setProperty(USERNAME_PROPERTY_KEY, originalUsername);
            }

            if (originalPassword == null) {
                System.clearProperty(PASSWORD_PROPERTY_KEY);
            } else {
                System.setProperty(PASSWORD_PROPERTY_KEY, originalPassword);
            }
        }
    }
}
