package com.relayrides.pushy.apns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.net.ssl.SSLException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import com.relayrides.pushy.apns.util.ApnsPayloadBuilder;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.util.concurrent.Future;

public class ApnsClientTest {

    private static NioEventLoopGroup EVENT_LOOP_GROUP;

    private static File SINGLE_TOPIC_CLIENT_CERTIFICATE;
    private static File SINGLE_TOPIC_CLIENT_PRIVATE_KEY;

    private static File MULTI_TOPIC_CLIENT_CERTIFICATE;
    private static File MULTI_TOPIC_CLIENT_PRIVATE_KEY;

    private static File UNTRUSTED_CLIENT_CERTIFICATE;
    private static File UNTRUSTED_CLIENT_PRIVATE_KEY;

    private static File CA_CERTIFICATE;

    private static final String HOST = "localhost";
    private static final int PORT = 8443;

    private static final String DEFAULT_TOPIC = "com.relayrides.pushy";

    private static final int TOKEN_LENGTH = 32; // bytes

    private MockApnsServer server;
    private ApnsClient<SimpleApnsPushNotification> client;

    @Rule
    public Timeout globalTimeout = new Timeout(10000);

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // We want enough threads so we can be confident that the client and server are running on different threads and
        // we can accurately simulate race conditions. Two threads seems like an obvious choice, but there are some
        // tests where we have multiple clients and servers in play, and it's good to have some extra room in those
        // cases.
        ApnsClientTest.EVENT_LOOP_GROUP = new NioEventLoopGroup(4);

        SINGLE_TOPIC_CLIENT_CERTIFICATE = new File(ApnsClientTest.class.getResource("/single-topic-client.pem").toURI());
        SINGLE_TOPIC_CLIENT_PRIVATE_KEY = new File(ApnsClientTest.class.getResource("/single-topic-client.pk8").toURI());

        MULTI_TOPIC_CLIENT_CERTIFICATE = new File(ApnsClientTest.class.getResource("/multi-topic-client.pem").toURI());
        MULTI_TOPIC_CLIENT_PRIVATE_KEY = new File(ApnsClientTest.class.getResource("/multi-topic-client.pk8").toURI());

        UNTRUSTED_CLIENT_CERTIFICATE = new File(ApnsClientTest.class.getResource("/untrusted-client.pem").toURI());
        UNTRUSTED_CLIENT_PRIVATE_KEY = new File(ApnsClientTest.class.getResource("/untrusted-client.pk8").toURI());

        CA_CERTIFICATE = new File(ApnsClientTest.class.getResource("/ca.pem").toURI());
    }

    @Before
    public void setUp() throws Exception {
        this.server = new MockApnsServer(EVENT_LOOP_GROUP);
        this.server.start(PORT).await();

        this.client = new ApnsClient<SimpleApnsPushNotification>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_CERTIFICATE, SINGLE_TOPIC_CLIENT_PRIVATE_KEY),
                EVENT_LOOP_GROUP, new NullMetrics());

        this.client.connect(HOST, PORT).await();
    }

    @After
    public void tearDown() throws Exception {
        this.client.disconnect().await();

        // Mild hack: there's a harmless race condition where we can try to write a `GOAWAY` from the server to the
        // client in the time between when the client closes the connection and the server notices the connection has
        // been closed. That results in a harmless but slightly alarming warning about failing to write a `GOAWAY` frame
        // and already-closed SSL engines. By sleeping here, we reduce the probability of that warning popping up in
        // test output and frightening users.
        Thread.sleep(10);

        this.server.shutdown().await();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        ApnsClientTest.EVENT_LOOP_GROUP.shutdownGracefully().await();
    }

    @Test
    public void testApnsClientWithManagedEventLoopGroup() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> managedGroupClient = new ApnsClient<SimpleApnsPushNotification>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_CERTIFICATE, SINGLE_TOPIC_CLIENT_PRIVATE_KEY), null, new NullMetrics());

        assertTrue(managedGroupClient.connect(HOST, PORT).await().isSuccess());
        assertTrue(managedGroupClient.disconnect().await().isSuccess());
    }

    @Test
    public void testRestartApnsClientWithManagedEventLoopGroup() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> managedGroupClient = new ApnsClient<SimpleApnsPushNotification>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_CERTIFICATE, SINGLE_TOPIC_CLIENT_PRIVATE_KEY), null, new NullMetrics());

        assertTrue(managedGroupClient.connect(HOST, PORT).await().isSuccess());
        assertTrue(managedGroupClient.disconnect().await().isSuccess());

        final Future<Void> reconnectFuture = managedGroupClient.connect(HOST, PORT);

        assertFalse(reconnectFuture.isSuccess());
        assertTrue(reconnectFuture.cause() instanceof IllegalStateException);
    }

    @Test
    public void testReconnectionAfterClose() throws Exception {
        assertTrue(this.client.isConnected());
        assertTrue(this.client.disconnect().await().isSuccess());

        assertFalse(this.client.isConnected());

        assertTrue(this.client.connect(HOST, PORT).await().isSuccess());
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

        this.server.start(PORT).await();

        // Wait for the client to reconnect automatically; if it doesn't, the test will time out and fail
        final Future<Void> reconnectionFuture = this.client.getReconnectionFuture();
        reconnectionFuture.await();

        assertTrue(reconnectionFuture.isSuccess());
        assertTrue(this.client.isConnected());
    }

    @Test
    public void testGetReconnectionFutureWhenConnected() throws Exception {
        final Future<Void> reconnectionFuture = this.client.getReconnectionFuture();
        reconnectionFuture.await();

        assertTrue(this.client.isConnected());
        assertTrue(reconnectionFuture.isSuccess());
    }

    @Test
    public void testGetReconnectionFutureWhenNotConnected() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> unconnectedClient = new ApnsClient<SimpleApnsPushNotification>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_CERTIFICATE, SINGLE_TOPIC_CLIENT_PRIVATE_KEY),
                EVENT_LOOP_GROUP, new NullMetrics());

        final Future<Void> reconnectionFuture = unconnectedClient.getReconnectionFuture();

        reconnectionFuture.await();

        assertFalse(unconnectedClient.isConnected());
        assertFalse(reconnectionFuture.isSuccess());
    }

    public void testConnectWithUntrustedCertificate() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> untrustedClient = new ApnsClient<SimpleApnsPushNotification>(
                ApnsClientTest.getSslContextForTestClient(UNTRUSTED_CLIENT_CERTIFICATE, UNTRUSTED_CLIENT_PRIVATE_KEY),
                EVENT_LOOP_GROUP, new NullMetrics());

        final Future<Void> connectFuture = untrustedClient.connect(HOST, PORT).await();
        assertFalse(connectFuture.isSuccess());
        assertTrue(connectFuture.cause() instanceof SSLException);

        untrustedClient.disconnect().await();
    }

    public void testSendNotificationBeforeConnected() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> unconnectedClient = new ApnsClient<SimpleApnsPushNotification>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_CERTIFICATE, SINGLE_TOPIC_CLIENT_PRIVATE_KEY),
                EVENT_LOOP_GROUP, new NullMetrics());

        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerToken(DEFAULT_TOPIC, testToken);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, null, "test-payload");
        final Future<PushNotificationResponse<SimpleApnsPushNotification>> sendFuture =
                unconnectedClient.sendNotification(pushNotification).await();

        assertFalse(sendFuture.isSuccess());
        assertTrue(sendFuture.cause() instanceof IllegalStateException);
    }

    @Test
    public void testSendNotification() throws Exception {
        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerToken(DEFAULT_TOPIC, testToken);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, null, "test-payload");
        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertTrue(response.isAccepted());
    }

    @Test
    public void testSendManyNotifications() throws Exception {
        final int notificationCount = 1000;

        final List<SimpleApnsPushNotification> pushNotifications = new ArrayList<SimpleApnsPushNotification>();

        for (int i = 0; i < notificationCount; i++) {
            final String token = ApnsClientTest.generateRandomToken();
            final String payload = ApnsClientTest.generateRandomPayload();

            this.server.registerToken(DEFAULT_TOPIC, token);
            pushNotifications.add(new SimpleApnsPushNotification(token, DEFAULT_TOPIC, payload));
        }

        final List<Future<PushNotificationResponse<SimpleApnsPushNotification>>> futures =
                new ArrayList<Future<PushNotificationResponse<SimpleApnsPushNotification>>>();

        for (final SimpleApnsPushNotification pushNotification : pushNotifications) {
            futures.add(this.client.sendNotification(pushNotification));
        }

        for (final Future<PushNotificationResponse<SimpleApnsPushNotification>> future : futures) {
            future.await();

            assertTrue(future.isSuccess());
            assertTrue(future.get().isAccepted());
        }
    }

    @Test
    public void testSendNotificationWithBadTopic() throws Exception {
        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerToken(DEFAULT_TOPIC, testToken);

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(testToken, "Definitely not a real topic", "test-payload", null,
                        DeliveryPriority.IMMEDIATE);

        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertFalse(response.isAccepted());
        assertEquals("TopicDisallowed", response.getRejectionReason());
        assertNull(response.getTokenInvalidationTimestamp());
    }

    @Test
    public void testSendNotificationWithMissingTopic() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> multiTopicClient = new ApnsClient<SimpleApnsPushNotification>(
                ApnsClientTest.getSslContextForTestClient(MULTI_TOPIC_CLIENT_CERTIFICATE, MULTI_TOPIC_CLIENT_PRIVATE_KEY),
                EVENT_LOOP_GROUP, new NullMetrics());

        multiTopicClient.connect(HOST, PORT).await();

        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerToken(DEFAULT_TOPIC, testToken);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, null, "test-payload");

        final PushNotificationResponse<SimpleApnsPushNotification> response =
                multiTopicClient.sendNotification(pushNotification).get();

        multiTopicClient.disconnect().await();

        assertFalse(response.isAccepted());
        assertEquals("MissingTopic", response.getRejectionReason());
        assertNull(response.getTokenInvalidationTimestamp());
    }

    @Test
    public void testSendNotificationWithSpecifiedTopic() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> multiTopicClient = new ApnsClient<SimpleApnsPushNotification>(
                ApnsClientTest.getSslContextForTestClient(MULTI_TOPIC_CLIENT_CERTIFICATE, MULTI_TOPIC_CLIENT_PRIVATE_KEY),
                EVENT_LOOP_GROUP, new NullMetrics());

        multiTopicClient.connect(HOST, PORT).await();

        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerToken(DEFAULT_TOPIC, testToken);

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(testToken, DEFAULT_TOPIC, "test-payload", null, DeliveryPriority.IMMEDIATE);

        final PushNotificationResponse<SimpleApnsPushNotification> response =
                multiTopicClient.sendNotification(pushNotification).get();

        multiTopicClient.disconnect().await();

        assertTrue(response.isAccepted());
    }

    @Test
    public void testSendNotificationWithUnregisteredToken() throws Exception {
        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(ApnsClientTest.generateRandomToken(), null, "test-payload");

        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertFalse(response.isAccepted());
        assertEquals("DeviceTokenNotForTopic", response.getRejectionReason());
        assertNull(response.getTokenInvalidationTimestamp());
    }

    @Test
    public void testSendNotificationWithExpiredToken() throws Exception {
        final String testToken = ApnsClientTest.generateRandomToken();

        // APNs uses timestamps rounded to the nearest second; for ease of comparison, we test with timestamps that
        // just happen to fall on whole seconds.
        final Date roundedNow = new Date((System.currentTimeMillis() / 1000) * 1000);

        this.server.registerToken(DEFAULT_TOPIC, testToken, roundedNow);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, null, "test-payload");
        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertFalse(response.isAccepted());
        assertEquals("Unregistered", response.getRejectionReason());
        assertEquals(roundedNow, response.getTokenInvalidationTimestamp());
    }

    private static SslContext getSslContextForTestClient(final File certificate, final File privateKey) throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException, IOException, CertificateException {
        return SslContextBuilder.forClient()
                .sslProvider(OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .keyManager(certificate, privateKey)
                .trustManager(CA_CERTIFICATE)
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

    private static String generateRandomPayload() {
        final ApnsPayloadBuilder payloadBuilder = new ApnsPayloadBuilder();
        payloadBuilder.setAlertBody(UUID.randomUUID().toString());

        return payloadBuilder.buildWithDefaultMaximumLength();
    }
}
