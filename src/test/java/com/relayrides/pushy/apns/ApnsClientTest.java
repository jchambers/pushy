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
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

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

    private static final String SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME = "/pushy-test-client-single-topic.jks";
    private static final String MULTI_TOPIC_CLIENT_KEYSTORE_FILENAME = "/pushy-test-client-multi-topic.jks";
    private static final String UNTRUSTED_CLIENT_KEYSTORE_FILENAME = "/pushy-test-client-untrusted.jks";
    private static final String CLIENT_KEYSTORE_PASSWORD = "pushy-test";

    private static final String DEFAULT_TOPIC = "com.relayrides.pushy";

    private static final String DEFAULT_ALGORITHM = "SunX509";

    private static final int TOKEN_LENGTH = 32; // bytes

    private MockApnsServer server;
    private ApnsClient<SimpleApnsPushNotification> client;

    @Rule
    public Timeout globalTimeout = new Timeout(10000);

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        ApnsClientTest.EVENT_LOOP_GROUP = new NioEventLoopGroup();
    }

    @Before
    public void setUp() throws Exception {
        this.server = new MockApnsServer(8443, EVENT_LOOP_GROUP);
        this.server.start().await();

        this.client = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME, CLIENT_KEYSTORE_PASSWORD),
                EVENT_LOOP_GROUP);

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

    @Test
    public void testReconnectionAfterClose() throws Exception {
        assertTrue(this.client.isConnected());
        this.client.disconnect().get();

        assertFalse(this.client.isConnected());

        this.client.connect("localhost", 8443).get();
        assertTrue(this.client.isConnected());
    }

    @Test
    public void testAutomaticReconnection() throws Exception {
        assertTrue(this.client.isConnected());

        this.server.shutdown().await();

        // Wait for the client to notice the GOAWAY; if it doesn't, the test will time out and fail
        while (this.client.isConnected()) {
            Thread.sleep(100);
        }

        assertFalse(this.client.isConnected());

        this.server.start().await();

        // Wait for the client to reconnect automatically; if it doesn't, the test will time out and fail
        while (!this.client.isConnected()) {
            Thread.sleep(500);
        }

        assertTrue(this.client.isConnected());
    }

    @Test(expected = ExecutionException.class)
    public void testConnectWithUntrustedCertificate() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> untrustedClient = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(UNTRUSTED_CLIENT_KEYSTORE_FILENAME, CLIENT_KEYSTORE_PASSWORD),
                EVENT_LOOP_GROUP);

        untrustedClient.connect("localhost", 8443).get();
        untrustedClient.disconnect().get();
    }

    @Test(expected = ExecutionException.class)
    public void testSendNotificationBeforeConnected() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> unconnectedClient = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME, CLIENT_KEYSTORE_PASSWORD),
                EVENT_LOOP_GROUP);

        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerToken(DEFAULT_TOPIC, testToken);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, "test-payload");
        unconnectedClient.sendNotification(pushNotification).get();
    }

    @Test
    public void testSendNotification() throws Exception {
        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerToken(DEFAULT_TOPIC, testToken);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, "test-payload");
        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertTrue(response.isSuccess());
    }

    @Test
    public void testSendNotificationWithBadTopic() throws Exception {
        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerToken(DEFAULT_TOPIC, testToken);

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(testToken, "test-payload", null, DeliveryPriority.IMMEDIATE,
                        "Definitely not a real topic");

        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertFalse(response.isSuccess());
        assertEquals("TopicDisallowed", response.getRejectionReason());
        assertNull(response.getTokenExpirationTimestamp());
    }

    @Test
    public void testSendNotificationWithMissingTopic() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> multiTopicClient = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(MULTI_TOPIC_CLIENT_KEYSTORE_FILENAME, CLIENT_KEYSTORE_PASSWORD),
                EVENT_LOOP_GROUP);

        multiTopicClient.connect("localhost", 8443).get();

        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerToken(DEFAULT_TOPIC, testToken);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, "test-payload");

        final PushNotificationResponse<SimpleApnsPushNotification> response =
                multiTopicClient.sendNotification(pushNotification).get();

        multiTopicClient.disconnect().get();

        assertFalse(response.isSuccess());
        assertEquals("MissingTopic", response.getRejectionReason());
        assertNull(response.getTokenExpirationTimestamp());
    }

    @Test
    public void testSendNotificationWithUnregisteredToken() throws Exception {
        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(ApnsClientTest.generateRandomToken(), "test-payload");

        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertFalse(response.isSuccess());
        assertEquals("DeviceTokenNotForTopic", response.getRejectionReason());
        assertNull(response.getTokenExpirationTimestamp());
    }

    @Test
    public void testSendNotificationWithExpiredToken() throws Exception {
        final String testToken = ApnsClientTest.generateRandomToken();

        // APNs uses timestamps rounded to the nearest second; for ease of comparison, we test with timestamps that
        // just happen to fall on whole seconds.
        final Date roundedNow = new Date((System.currentTimeMillis() / 1000) * 1000);

        this.server.registerToken(DEFAULT_TOPIC, testToken, roundedNow);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, "test-payload");
        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertFalse(response.isSuccess());
        assertEquals("Unregistered", response.getRejectionReason());
        assertEquals(roundedNow, response.getTokenExpirationTimestamp());
    }

    private static SslContext getSslContextForTestClient(final String keyStoreFilename, final String keyStorePassword) throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException, IOException, CertificateException {
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

    private static String generateRandomToken() {
        final byte[] tokenBytes = new byte[TOKEN_LENGTH];
        new Random().nextBytes(tokenBytes);

        final StringBuilder builder = new StringBuilder(TOKEN_LENGTH * 2);

        for (final byte b : tokenBytes) {
            final String hexString = Integer.toHexString(b & 0xff);

            if (hexString.length() == 1) {
                // We need a leading zero
                builder.append('0');
            }

            builder.append(hexString);
        }

        return builder.toString();
    }
}
