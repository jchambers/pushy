package com.relayrides.pushy.apns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.security.interfaces.ECPublicKey;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import io.netty.channel.nio.NioEventLoopGroup;

public class MockApnsServerTest {

    private static final String SERVER_CERTIFICATE_FILENAME = "/server.pem";
    private static final String SERVER_KEY_FILENAME = "/server.key";

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
    public void testRegisterPublicKey() throws Exception {
        final String teamId = "team-id";
        final String firstKeyId = "key-id";
        final String secondKeyId = "different-key-id";
        final String firstTopic = "first-topic";
        final String secondTopic = "second-topic";

        this.server.registerPublicKey((ECPublicKey) KeyPairUtil.generateKeyPair().getPublic(), teamId, firstKeyId, firstTopic);

        assertNotNull(this.server.getSignatureForKeyId(firstKeyId));
        assertEquals(teamId, this.server.getTeamIdForKeyId(firstKeyId));
        assertEquals(1, this.server.getTopicsForTeamId(teamId).size());
        assertTrue(this.server.getTopicsForTeamId(teamId).contains(firstTopic));

        this.server.registerPublicKey((ECPublicKey) KeyPairUtil.generateKeyPair().getPublic(), teamId, secondKeyId, secondTopic);

        assertNull(this.server.getSignatureForKeyId(firstKeyId));
        assertNotNull(this.server.getSignatureForKeyId(secondKeyId));
        assertNull(this.server.getTeamIdForKeyId(firstKeyId));
        assertEquals(teamId, this.server.getTeamIdForKeyId(secondKeyId));
        assertEquals(1, this.server.getTopicsForTeamId(teamId).size());
        assertTrue(this.server.getTopicsForTeamId(teamId).contains(secondTopic));
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
