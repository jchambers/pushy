package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.security.KeyStore;
import java.util.concurrent.ExecutionException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

import io.netty.channel.nio.NioEventLoopGroup;

public class ApnsClientTest {

    private static NioEventLoopGroup EVENT_LOOP_GROUP;
    private static KeyStore CLIENT_KEY_STORE;

    private static final String CLIENT_KEYSTORE_FILENAME = "/pushy-test-client.jks";
    private static final String UNTRUSTED_CLIENT_KEYSTORE_FILENAME = "/pushy-test-client-untrusted.jks";
    private static final String CLIENT_KEYSTORE_PASSWORD = "pushy-test";

    private MockApnsServer server;
    private ApnsClient<SimpleApnsPushNotification> client;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        ApnsClientTest.EVENT_LOOP_GROUP = new NioEventLoopGroup();

        try (final InputStream keyStoreInputStream = ApnsClientTest.class.getResourceAsStream(CLIENT_KEYSTORE_FILENAME)) {
            if (keyStoreInputStream == null) {
                throw new RuntimeException("Client keystore file not found.");
            }

            CLIENT_KEY_STORE = KeyStore.getInstance("JKS");
            CLIENT_KEY_STORE.load(keyStoreInputStream, "pushy-test".toCharArray());
        }
    }

    @Before
    public void setUp() throws Exception {
        this.server = new MockApnsServer(8443, EVENT_LOOP_GROUP);
        this.server.start().await();

        this.client = new ApnsClient<>("localhost", 8443, CLIENT_KEY_STORE, CLIENT_KEYSTORE_PASSWORD, EVENT_LOOP_GROUP);
        this.client.connect().get();
    }

    @After
    public void tearDown() throws Exception {
        this.client.close().await();
        this.server.shutdown().await();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        ApnsClientTest.EVENT_LOOP_GROUP.shutdownGracefully().await();
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

    public void testConnectWithUntrustedCertificate() throws Exception {
        final KeyStore untrustedKeyStore;

        try (final InputStream keyStoreInputStream = ApnsClientTest.class.getResourceAsStream(UNTRUSTED_CLIENT_KEYSTORE_FILENAME)) {
            untrustedKeyStore = KeyStore.getInstance("JKS");
            untrustedKeyStore.load(keyStoreInputStream, CLIENT_KEYSTORE_PASSWORD.toCharArray());
        }

        final ApnsClient<SimpleApnsPushNotification> untrustedClient =
                new ApnsClient<>("localhost", 8443, CLIENT_KEY_STORE, CLIENT_KEYSTORE_PASSWORD, EVENT_LOOP_GROUP);

        try {
            untrustedClient.connect().get();
            fail("Connection attempt should fail due to untrusted certificate.");
        } catch (final ExecutionException e) {
            untrustedClient.close().await();
        }
    }
}
