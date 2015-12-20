package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;

public class ApnsClientTest {

    private static NioEventLoopGroup EVENT_LOOP_GROUP;

    private static final String CLIENT_KEYSTORE_FILENAME = "/pushy-test-client.jks";
    private static final String UNTRUSTED_CLIENT_KEYSTORE_FILENAME = "/pushy-test-client-untrusted.jks";
    private static final String CLIENT_KEYSTORE_PASSWORD = "pushy-test";

    private static final String DEFAULT_ALGORITHM = "SunX509";

    private MockApnsServer server;
    private ApnsClient<SimpleApnsPushNotification> client;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        ApnsClientTest.EVENT_LOOP_GROUP = new NioEventLoopGroup();
    }

    @Before
    public void setUp() throws Exception {
        this.server = new MockApnsServer(8443, EVENT_LOOP_GROUP);
        this.server.start().await();

        this.client = new ApnsClient<>(
                this.getSslContextForTestClient(CLIENT_KEYSTORE_FILENAME, CLIENT_KEYSTORE_PASSWORD), EVENT_LOOP_GROUP);

        this.client.connect("localhost", 8443).get();
    }

    @After
    public void tearDown() throws Exception {
        this.client.disconnect().get();
        this.server.shutdown().await();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        ApnsClientTest.EVENT_LOOP_GROUP.shutdownGracefully().await();
    }

    @Test(expected = ExecutionException.class)
    public void testConnectWithUntrustedCertificate() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> untrustedClient = new ApnsClient<>(
                this.getSslContextForTestClient(UNTRUSTED_CLIENT_KEYSTORE_FILENAME, CLIENT_KEYSTORE_PASSWORD),
                EVENT_LOOP_GROUP);

        untrustedClient.connect("localhost", 8443).get();
        untrustedClient.disconnect().get();
    }

    @Test
    public void testSendNotification() throws Exception {
        final String testToken = TokenTestUtil.generateRandomToken();

        this.server.registerToken(testToken);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, "test-payload");
        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertTrue(response.isSuccess());
    }

    private SslContext getSslContextForTestClient(final String keyStoreFilename, final String keyStorePassword) throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException, IOException, CertificateException {
        final String algorithm;
        {
            final String algorithmFromSecurityProperties = Security.getProperty("ssl.KeyManagerFactory.algorithm");
            algorithm = algorithmFromSecurityProperties != null ? algorithmFromSecurityProperties : DEFAULT_ALGORITHM;
        }

        final KeyStore keyStore = KeyStore.getInstance("JKS");

        try (final InputStream keyStoreInputStream = ApnsClientTest.class.getResourceAsStream(keyStoreFilename)) {
            if (keyStoreInputStream == null) {
                throw new KeyStoreException("Client keystore file not found.");
            }

            keyStore.load(keyStoreInputStream, "pushy-test".toCharArray());
        }

        final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
        trustManagerFactory.init(keyStore);

        final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
        keyManagerFactory.init(keyStore, CLIENT_KEYSTORE_PASSWORD.toCharArray());

        return SslContextBuilder.forClient()
                .sslProvider(OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .keyManager(keyManagerFactory)
                .trustManager(trustManagerFactory)
                .applicationProtocolConfig(new ApplicationProtocolConfig(Protocol.ALPN,
                        SelectorFailureBehavior.NO_ADVERTISE,
                        SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2))
                .build();
    }
}
