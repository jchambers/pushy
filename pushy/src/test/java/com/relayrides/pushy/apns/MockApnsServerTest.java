package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import io.netty.channel.nio.NioEventLoopGroup;

public class MockApnsServerTest {

    private static final String SERVER_CERTIFICATE_FILENAME = "/server-cert.pem";
    private static final String SERVER_KEY_FILENAME = "/server-key.pem";

    private static final int PORT = 8443;

    private MockApnsServer server;

    @Before
    public void setUp() throws Exception {
        try (final InputStream certificateInputStream = MockApnsServerTest.class.getResourceAsStream(SERVER_CERTIFICATE_FILENAME);
                final InputStream keyInputStream = MockApnsServerTest.class.getResourceAsStream(SERVER_KEY_FILENAME)) {

            this.server = new MockApnsServerBuilder()
                    .setServerCredentials(certificateInputStream, keyInputStream, null)
                    .build();
        }
    }

    @Test
    public void testAddToken() {
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

        try (final InputStream certificateInputStream = MockApnsServerTest.class.getResourceAsStream(SERVER_CERTIFICATE_FILENAME);
                final InputStream keyInputStream = MockApnsServerTest.class.getResourceAsStream(SERVER_KEY_FILENAME)) {

            final MockApnsServer providedGroupServer = new MockApnsServerBuilder()
                    .setServerCredentials(certificateInputStream, keyInputStream, null)
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

        try (final InputStream certificateInputStream = MockApnsServerTest.class.getResourceAsStream(SERVER_CERTIFICATE_FILENAME);
                final InputStream keyInputStream = MockApnsServerTest.class.getResourceAsStream(SERVER_KEY_FILENAME)) {

            final MockApnsServer providedGroupServer = new MockApnsServerBuilder()
                    .setServerCredentials(certificateInputStream, keyInputStream, null)
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
