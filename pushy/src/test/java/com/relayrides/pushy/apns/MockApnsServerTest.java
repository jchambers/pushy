package com.relayrides.pushy.apns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import io.netty.channel.nio.NioEventLoopGroup;

public class MockApnsServerTest {

    private static final String SERVER_CERTIFICATES_FILENAME = "/server_certs.pem";
    private static final String SERVER_KEY_FILENAME = "/server_key.pem";

    private static final int PORT = 8443;

    private MockApnsServer server;

    @Before
    public void setUp() throws Exception {
        this.server = new MockApnsServerBuilder()
                .setServerCredentials(MockApnsServerTest.class.getResourceAsStream(SERVER_CERTIFICATES_FILENAME), MockApnsServerTest.class.getResourceAsStream(SERVER_KEY_FILENAME), null)
                .build();
    }

    @Test
    public void testRegisterTokenForTopic() {
        final String token = "example-token";
        final String topic = "com.example.topic";

        assertFalse(this.server.isTokenRegisteredForTopic(token, topic));

        this.server.registerDeviceTokenForTopic(topic, token, null);
        assertTrue(this.server.isTokenRegisteredForTopic(token, topic));
    }

    @Test
    public void testClearTokens() {
        final String token = "example-token";
        final String topic = "com.example.topic";

        this.server.registerDeviceTokenForTopic(topic, token, null);
        assertTrue(this.server.isTokenRegisteredForTopic(token, topic));

        this.server.clearTokens();
        assertFalse(this.server.isTokenRegisteredForTopic(token, topic));
    }

    @Test
    public void testGetExpirationTimestampForTokenInTopic() {
        final String topic = "com.example.topic";

        {
            final String token = "example-token";
            final Date expiration = new Date();

            this.server.registerDeviceTokenForTopic(topic, token, expiration);
            assertEquals(expiration, this.server.getExpirationTimestampForTokenInTopic(token, topic));
        }

        this.server.clearTokens();

        {
            final String token = "token-without-expiration";

            this.server.registerDeviceTokenForTopic(topic, token, null);
            assertNull(this.server.getExpirationTimestampForTokenInTopic(token, topic));
        }
    }

    @Test
    public void testStartAndShutdown() throws Exception {
        assertTrue(this.server.start(PORT).await().isSuccess());
        assertTrue(this.server.shutdown().await().isSuccess());
    }

    @Test
    public void testShutdownBeforeStart() throws Exception {
        assertTrue(this.server.shutdown().await().isSuccess());
    }

    @Test
    public void testShutdownWithProvidedEventLoopGroup() throws Exception {
        final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);

        try {

            final MockApnsServer providedGroupServer = new MockApnsServerBuilder()
                    .setServerCredentials(MockApnsServerTest.class.getResourceAsStream(SERVER_CERTIFICATES_FILENAME), MockApnsServerTest.class.getResourceAsStream(SERVER_KEY_FILENAME), null)
                    .setEventLoopGroup(eventLoopGroup)
                    .build();

            assertTrue(providedGroupServer.start(PORT).await().isSuccess());
            assertTrue(providedGroupServer.shutdown().await().isSuccess());

            assertFalse(eventLoopGroup.isShutdown());
        } finally {
            eventLoopGroup.shutdownGracefully();
        }
    }

    @Test
    public void testRestartWithProvidedEventLoopGroup() throws Exception {
        final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);

        try {

            final MockApnsServer providedGroupServer = new MockApnsServerBuilder()
                    .setServerCredentials(MockApnsServerTest.class.getResourceAsStream(SERVER_CERTIFICATES_FILENAME), MockApnsServerTest.class.getResourceAsStream(SERVER_KEY_FILENAME), null)
                    .setEventLoopGroup(eventLoopGroup)
                    .build();

            assertTrue(providedGroupServer.start(PORT).await().isSuccess());
            assertTrue(providedGroupServer.shutdown().await().isSuccess());
            assertTrue(providedGroupServer.start(PORT).await().isSuccess());
            assertTrue(providedGroupServer.shutdown().await().isSuccess());
        } finally {
            eventLoopGroup.shutdownGracefully();
        }
    }
}
