package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.security.KeyStore;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

import io.netty.channel.nio.NioEventLoopGroup;

public class ApnsClientTest {

    private static NioEventLoopGroup EVENT_LOOP_GROUP;

    private static final String CLIENT_KEYSTORE_FILENAME = "/pushy-test-client.jks";
    private static final String CLIENT_KEYSTORE_PASSWORD = "pushy-test";

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        ApnsClientTest.EVENT_LOOP_GROUP = new NioEventLoopGroup();
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        ApnsClientTest.EVENT_LOOP_GROUP.shutdownGracefully().await();
    }

    @Test
    public void testSendNotification() throws Exception {
        final MockApnsServer server = new MockApnsServer(8443, EVENT_LOOP_GROUP);
        server.start().await();

        final InputStream keyStoreInputStream = ApnsClientTest.class.getResourceAsStream(CLIENT_KEYSTORE_FILENAME);

        if (keyStoreInputStream == null) {
            throw new RuntimeException("Client keystore file not found.");
        }

        final KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(keyStoreInputStream, "pushy-test".toCharArray());

        final ApnsClient<SimpleApnsPushNotification> client = new ApnsClient<>("localhost", 8443, keyStore, CLIENT_KEYSTORE_PASSWORD, EVENT_LOOP_GROUP);

        client.connect().get();

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification("test-token", "test-payload");
        final PushNotificationResponse<SimpleApnsPushNotification> response = client.sendNotification(pushNotification).get();

        assertTrue(response.isSuccess());

        client.close().await();
        server.shutdown().await();
    }

}
