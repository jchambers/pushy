package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.relayrides.pushy.apns.util.ApnsPayloadBuilder;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class ApnsClientTest {

    private static NioEventLoopGroup EVENT_LOOP_GROUP;

    private static final String CA_CERTIFICATE_FILENAME = "/ca-cert.pem";
    private static final String SERVER_CERTIFICATE_FILENAME = "/server-cert.pem";
    private static final String SERVER_KEY_FILENAME = "/server-key.pem";

    private static final String HOST = "localhost";
    private static final int PORT = 8443;

    private static final String DEFAULT_TEAM = "TEST";
    private static final String DEFAULT_KEY_ID = "test-key";
    private static final String DEFAULT_TOPIC = "com.relayrides.pushy";

    private static final int TOKEN_LENGTH = 32; // bytes

    private MockApnsServer server;
    private ApnsClient<SimpleApnsPushNotification> client;

    private static class TestMetricsListener implements ApnsClientMetricsListener {

        private final List<Long> writeFailures = new ArrayList<>();
        private final List<Long> sentNotifications = new ArrayList<>();
        private final List<Long> acceptedNotifications = new ArrayList<>();
        private final List<Long> rejectedNotifications = new ArrayList<>();

        private final AtomicInteger connectionAttemptsStarted = new AtomicInteger(0);
        private final AtomicInteger successfulConnectionAttempts = new AtomicInteger(0);
        private final AtomicInteger failedConnectionAttempts = new AtomicInteger(0);

        @Override
        public void handleWriteFailure(final ApnsClient<? extends ApnsPushNotification> apnsClient, final long notificationId) {
            synchronized (this.writeFailures) {
                this.writeFailures.add(notificationId);
                this.writeFailures.notifyAll();
            }
        }

        @Override
        public void handleNotificationSent(final ApnsClient<? extends ApnsPushNotification> apnsClient, final long notificationId) {
            this.sentNotifications.add(notificationId);
        }

        @Override
        public void handleNotificationAccepted(final ApnsClient<? extends ApnsPushNotification> apnsClient, final long notificationId) {
            synchronized (this.acceptedNotifications) {
                this.acceptedNotifications.add(notificationId);
                this.acceptedNotifications.notifyAll();
            }
        }

        @Override
        public void handleNotificationRejected(final ApnsClient<? extends ApnsPushNotification> apnsClient, final long notificationId) {
            synchronized (this.rejectedNotifications) {
                this.rejectedNotifications.add(notificationId);
                this.rejectedNotifications.notifyAll();
            }
        }

        @Override
        public void handleConnectionAttemptStarted(final ApnsClient<? extends ApnsPushNotification> apnsClient) {
            this.connectionAttemptsStarted.getAndIncrement();
        }

        @Override
        public void handleConnectionAttemptSucceeded(final ApnsClient<? extends ApnsPushNotification> apnsClient) {
            synchronized (this.successfulConnectionAttempts) {
                this.successfulConnectionAttempts.getAndIncrement();
                this.successfulConnectionAttempts.notifyAll();
            }
        }

        @Override
        public void handleConnectionAttemptFailed(final ApnsClient<? extends ApnsPushNotification> apnsClient) {
            synchronized (this.failedConnectionAttempts) {
                this.failedConnectionAttempts.getAndIncrement();
                this.failedConnectionAttempts.notifyAll();
            }
        }

        public void waitForNonZeroWriteFailures() throws InterruptedException {
            synchronized (this.writeFailures) {
                while (this.writeFailures.isEmpty()) {
                    this.writeFailures.wait();
                }
            }
        }

        public void waitForNonZeroAcceptedNotifications() throws InterruptedException {
            synchronized (this.acceptedNotifications) {
                while (this.acceptedNotifications.isEmpty()) {
                    this.acceptedNotifications.wait();
                }
            }
        }

        public void waitForNonZeroRejectedNotifications() throws InterruptedException {
            synchronized (this.rejectedNotifications) {
                while (this.rejectedNotifications.isEmpty()) {
                    this.rejectedNotifications.wait();
                }
            }
        }

        public void waitForNonZeroSuccessfulConnections() throws InterruptedException {
            synchronized (this.successfulConnectionAttempts) {
                while (this.successfulConnectionAttempts.get() == 0) {
                    this.successfulConnectionAttempts.wait();
                }
            }
        }

        public void waitForNonZeroFailedConnections() throws InterruptedException {
            synchronized (this.failedConnectionAttempts) {
                while (this.failedConnectionAttempts.get() == 0) {
                    this.failedConnectionAttempts.wait();
                }
            }
        }

        public List<Long> getWriteFailures() {
            return this.writeFailures;
        }

        public List<Long> getSentNotifications() {
            return this.sentNotifications;
        }

        public List<Long> getAcceptedNotifications() {
            return this.acceptedNotifications;
        }

        public List<Long> getRejectedNotifications() {
            return this.rejectedNotifications;
        }

        public AtomicInteger getConnectionAttemptsStarted() {
            return this.connectionAttemptsStarted;
        }

        public AtomicInteger getSuccessfulConnectionAttempts() {
            return this.successfulConnectionAttempts;
        }

        public AtomicInteger getFailedConnectionAttempts() {
            return this.failedConnectionAttempts;
        }
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // We want enough threads so we can be confident that the client and server are running on different threads and
        // we can accurately simulate race conditions. Two threads seems like an obvious choice, but there are some
        // tests where we have multiple clients and servers in play, and it's good to have some extra room in those
        // cases.
        ApnsClientTest.EVENT_LOOP_GROUP = new NioEventLoopGroup(4);
    }

    @Before
    public void setUp() throws Exception {
        final KeyPair keyPair = this.generateKeyPair();

        try (final InputStream serverCertifiacteInputStream = ApnsClientTest.class.getResourceAsStream(SERVER_CERTIFICATE_FILENAME);
                final InputStream serverKeyInputStream = ApnsClientTest.class.getResourceAsStream(SERVER_KEY_FILENAME);
                final InputStream caCertificateInputStream = ApnsClientTest.class.getResourceAsStream(CA_CERTIFICATE_FILENAME)) {

            this.server = new MockApnsServerBuilder()
                    .setServerCredentials(serverCertifiacteInputStream, serverKeyInputStream, null)
                    .setTrustedClientCertificateChain(caCertificateInputStream)
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
                    .build();
        }

        this.server.registerPublicKey(DEFAULT_TEAM, DEFAULT_KEY_ID, keyPair.getPublic());
        this.server.registerTopicsForTeamId(DEFAULT_TEAM, DEFAULT_TOPIC);

        this.server.start(PORT).await();

        try (final InputStream caCertificateInputStream = ApnsClientTest.class.getResourceAsStream(CA_CERTIFICATE_FILENAME)) {
            this.client = new ApnsClientBuilder<SimpleApnsPushNotification>()
                    .setTrustedServerCertificateChain(caCertificateInputStream)
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
                    .build();
        }

        this.client.registerSigningKey(DEFAULT_TEAM, DEFAULT_KEY_ID, keyPair.getPrivate());
        this.client.registerTopicsForTeamId(DEFAULT_TEAM, DEFAULT_TOPIC);

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
        final ApnsClient<SimpleApnsPushNotification> managedGroupClient;

        try (final InputStream caCertificateInputStream = ApnsClientTest.class.getResourceAsStream(CA_CERTIFICATE_FILENAME)) {
            managedGroupClient = new ApnsClientBuilder<SimpleApnsPushNotification>()
                    .setTrustedServerCertificateChain(caCertificateInputStream)
                    .build();
        }

        assertTrue(managedGroupClient.connect(HOST, PORT).await().isSuccess());
        assertTrue(managedGroupClient.disconnect().await().isSuccess());
    }

    @Test
    public void testSetFlushThresholds() {
        // We're happy here as long as nothing explodes
        this.client.setFlushThresholds(0, 0);
        this.client.setFlushThresholds(1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetFlushThresholdsWithZeroNotificationCount() {
        this.client.setFlushThresholds(0, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetFlushThresholdsWithZeroTimeout() {
        this.client.setFlushThresholds(1,  0);
    }

    @Test
    public void testRestartApnsClientWithManagedEventLoopGroup() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> managedGroupClient;

        try (final InputStream caCertificateInputStream = ApnsClientTest.class.getResourceAsStream(CA_CERTIFICATE_FILENAME)) {
            managedGroupClient = new ApnsClientBuilder<SimpleApnsPushNotification>()
                    .setTrustedServerCertificateChain(caCertificateInputStream)
                    .build();
        }

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
        final ApnsClient<SimpleApnsPushNotification> unconnectedClient;

        try (final InputStream caCertificateInputStream = ApnsClientTest.class.getResourceAsStream(CA_CERTIFICATE_FILENAME)) {
            unconnectedClient = new ApnsClientBuilder<SimpleApnsPushNotification>()
                    .setTrustedServerCertificateChain(caCertificateInputStream)
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
                    .build();
        }

        final Future<Void> reconnectionFuture = unconnectedClient.getReconnectionFuture();

        reconnectionFuture.await();

        assertFalse(unconnectedClient.isConnected());
        assertFalse(reconnectionFuture.isSuccess());
    }

    @Test
    public void testSendNotificationBeforeConnected() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> unconnectedClient;

        try (final InputStream caCertificateInputStream = ApnsClientTest.class.getResourceAsStream(CA_CERTIFICATE_FILENAME)) {
            unconnectedClient = new ApnsClientBuilder<SimpleApnsPushNotification>()
                    .setTrustedServerCertificateChain(caCertificateInputStream)
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
                    .build();
        }

        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, testToken, null);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, DEFAULT_TOPIC, "test-payload");
        final Future<PushNotificationResponse<SimpleApnsPushNotification>> sendFuture =
                unconnectedClient.sendNotification(pushNotification).await();

        assertFalse(sendFuture.isSuccess());
        assertTrue(sendFuture.cause() instanceof IllegalStateException);
    }

    @Test
    public void testSendNotification() throws Exception {
        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, testToken, null);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, DEFAULT_TOPIC, "test-payload");
        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertTrue(response.isAccepted());
    }

    /*
     * This addresses an issue identified in https://github.com/relayrides/pushy/issues/283 where the initial SETTINGS
     * frame from the server would trigger a flush, which was masking some issues with the deferred flush code.
     */
    @Test
    public void testSendNotificationAfterInitialSettings() throws Exception {
        this.client.waitForInitialSettings();

        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, testToken, null);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, DEFAULT_TOPIC, "test-payload");
        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertTrue(response.isAccepted());
    }

    @Test
    public void testSendNotificationWithImmediateFlush() throws Exception {
        this.client.disconnect().await();
        this.client.setFlushThresholds(0, 0);
        this.client.connect(HOST, PORT).await();

        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, testToken, null);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, DEFAULT_TOPIC, "test-payload");
        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertTrue(response.isAccepted());
    }

    @Test
    public void testSendManyNotifications() throws Exception {
        final int notificationCount = 1000;

        final List<SimpleApnsPushNotification> pushNotifications = new ArrayList<>();

        for (int i = 0; i < notificationCount; i++) {
            final String token = ApnsClientTest.generateRandomToken();
            final String payload = ApnsClientTest.generateRandomPayload();

            this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, token, null);
            pushNotifications.add(new SimpleApnsPushNotification(token, DEFAULT_TOPIC, payload));
        }

        final List<Future<PushNotificationResponse<SimpleApnsPushNotification>>> futures = new ArrayList<>();

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
    public void testSendManyNotificationsWithListeners() throws Exception {
        final int notificationCount = 1000;

        final List<SimpleApnsPushNotification> pushNotifications = new ArrayList<>();

        for (int i = 0; i < notificationCount; i++) {
            final String token = ApnsClientTest.generateRandomToken();
            final String payload = ApnsClientTest.generateRandomPayload();

            this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, token, null);
            pushNotifications.add(new SimpleApnsPushNotification(token, DEFAULT_TOPIC, payload));
        }

        final CountDownLatch countDownLatch = new CountDownLatch(notificationCount);

        for (final SimpleApnsPushNotification pushNotification : pushNotifications) {
            final Future<PushNotificationResponse<SimpleApnsPushNotification>> future =
                    this.client.sendNotification(pushNotification);

            future.addListener(new GenericFutureListener<Future<PushNotificationResponse<SimpleApnsPushNotification>>>() {

                @Override
                public void operationComplete(final Future<PushNotificationResponse<SimpleApnsPushNotification>> future) throws Exception {
                    if (future.isSuccess()) {
                        final PushNotificationResponse<SimpleApnsPushNotification> pushNotificationResponse = future.get();

                        if (pushNotificationResponse.isAccepted()) {
                            countDownLatch.countDown();
                        }
                    }
                }
            });
        }

        countDownLatch.await();
    }

    // See https://github.com/relayrides/pushy/issues/256
    @Test
    public void testRepeatedlySendSameNotification() throws Exception {
        final int notificationCount = 1000;

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(
                ApnsClientTest.generateRandomToken(), DEFAULT_TOPIC, ApnsClientTest.generateRandomPayload());

        final CountDownLatch countDownLatch = new CountDownLatch(notificationCount);

        for (int i = 0; i < notificationCount; i++) {
            final Future<PushNotificationResponse<SimpleApnsPushNotification>> future =
                    this.client.sendNotification(pushNotification);

            future.addListener(new GenericFutureListener<Future<PushNotificationResponse<SimpleApnsPushNotification>>>() {

                @Override
                public void operationComplete(final Future<PushNotificationResponse<SimpleApnsPushNotification>> future) throws Exception {
                    // All we're concerned with here is that the client told us SOMETHING about what happened to the
                    // notification
                    countDownLatch.countDown();
                }
            });
        }

        countDownLatch.await();
    }

    @Test
    public void testSendNotificationWithUnregisteredDeviceToken() throws Exception {
        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(ApnsClientTest.generateRandomToken(), DEFAULT_TOPIC, "test-payload");

        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertFalse(response.isAccepted());
        assertEquals("DeviceTokenNotForTopic", response.getRejectionReason());
        assertNull(response.getTokenInvalidationTimestamp());
    }

    @Test
    public void testSendNotificationWithExpiredDeviceToken() throws Exception {
        final String testToken = ApnsClientTest.generateRandomToken();

        final Date now = new Date();

        this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, testToken, now);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, DEFAULT_TOPIC, "test-payload");
        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertFalse(response.isAccepted());
        assertEquals("Unregistered", response.getRejectionReason());
        assertEquals(now, response.getTokenInvalidationTimestamp());
    }

    @Test
    public void testSendNotificationWithNoTeamForTopic() throws Exception {
        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, testToken, null);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, "Unregistered topic", "test-payload");

        try {
            this.client.sendNotification(pushNotification).get();
            fail("Expected NoKeyForTopicException as a cause for an ExecutionException.");
        } catch (final ExecutionException e) {
            assertTrue(e.getCause() instanceof NoKeyForTopicException);
        }
    }

    @Test
    public void testSendNotificationWithNoPublicKeyForTeam() throws Exception {
        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, testToken, null);

        final String topicWithoutKey = "topic-without-key";
        this.client.registerTopicsForTeamId("team-without-key", topicWithoutKey);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, topicWithoutKey, "test-payload");

        try {
            this.client.sendNotification(pushNotification).get();
            fail("Expected NoKeyForTopicException as a cause for an ExecutionException.");
        } catch (final ExecutionException e) {
            assertTrue(e.getCause() instanceof NoKeyForTopicException);
        }
    }

    @Test
    public void testSendNotificationWithExpiredAuthenticationToken() throws Exception {
        // This is a little roundabout, but it makes sure that we're going to be using an expired auth token for the
        // first shot at sending the notification.
        final AuthenticationTokenSupplier supplier = this.client.getAuthenticationTokenSupplierForTopic(DEFAULT_TOPIC);

        final String initialToken = supplier.getToken();
        supplier.invalidateToken(initialToken);

        final String expiredToken = supplier.getToken(new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)));

        assertNotEquals(initialToken, expiredToken);
        assertEquals(expiredToken, supplier.getToken());

        final String testToken = ApnsClientTest.generateRandomToken();

        this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, testToken, null);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, DEFAULT_TOPIC, "test-payload");
        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertTrue(response.isAccepted());
        assertNotEquals(expiredToken, supplier.getToken());
    }

    @Test
    public void testWriteFailureMetrics() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> unconnectedClient;

        try (final InputStream caCertificateInputStream = ApnsClientTest.class.getResourceAsStream(CA_CERTIFICATE_FILENAME)) {
            unconnectedClient = new ApnsClientBuilder<SimpleApnsPushNotification>()
                    .setTrustedServerCertificateChain(caCertificateInputStream)
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
                    .build();
        }

        final TestMetricsListener metricsListener = new TestMetricsListener();
        unconnectedClient.setMetricsListener(metricsListener);

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(ApnsClientTest.generateRandomToken(), DEFAULT_TOPIC, ApnsClientTest.generateRandomPayload());

        final Future<PushNotificationResponse<SimpleApnsPushNotification>> sendFuture =
                unconnectedClient.sendNotification(pushNotification);

        sendFuture.await();

        // Metrics listeners may be notified of write failures some time after the future actually fails
        metricsListener.waitForNonZeroWriteFailures();

        assertFalse(sendFuture.isSuccess());
        assertEquals(1, metricsListener.getWriteFailures().size());
    }

    @Test
    public void testAcceptedNotificationMetrics() throws Exception {
        final TestMetricsListener metricsListener = new TestMetricsListener();
        this.client.setMetricsListener(metricsListener);

        this.testSendNotification();
        metricsListener.waitForNonZeroAcceptedNotifications();

        assertEquals(1, metricsListener.getSentNotifications().size());
        assertEquals(metricsListener.getSentNotifications(), metricsListener.getAcceptedNotifications());
        assertTrue(metricsListener.getRejectedNotifications().isEmpty());
    }

    @Test
    public void testRejectedNotificationMetrics() throws Exception {
        final TestMetricsListener metricsListener = new TestMetricsListener();
        this.client.setMetricsListener(metricsListener);

        this.testSendNotificationWithUnregisteredDeviceToken();
        metricsListener.waitForNonZeroRejectedNotifications();

        assertEquals(1, metricsListener.getSentNotifications().size());
        assertEquals(metricsListener.getSentNotifications(), metricsListener.getRejectedNotifications());
        assertTrue(metricsListener.getAcceptedNotifications().isEmpty());
    }

    @Test
    public void testSuccessfulConnectionMetrics() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> unconnectedClient;

        try (final InputStream caCertificateInputStream = ApnsClientTest.class.getResourceAsStream(CA_CERTIFICATE_FILENAME)) {
            unconnectedClient = new ApnsClientBuilder<SimpleApnsPushNotification>()
                    .setTrustedServerCertificateChain(caCertificateInputStream)
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
                    .build();
        }

        final TestMetricsListener metricsListener = new TestMetricsListener();
        unconnectedClient.setMetricsListener(metricsListener);

        final Future<Void> connectionFuture = unconnectedClient.connect(HOST, PORT);
        connectionFuture.await();

        metricsListener.waitForNonZeroSuccessfulConnections();

        assertTrue(connectionFuture.isSuccess());
        assertEquals(1, metricsListener.getConnectionAttemptsStarted().get());
        assertEquals(1, metricsListener.getSuccessfulConnectionAttempts().get());
        assertEquals(0, metricsListener.getFailedConnectionAttempts().get());
    }

    @Test
    public void testFailedConnectionMetrics() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> unconnectedClient;

        try (final InputStream caCertificateInputStream = ApnsClientTest.class.getResourceAsStream(CA_CERTIFICATE_FILENAME)) {
            unconnectedClient = new ApnsClientBuilder<SimpleApnsPushNotification>()
                    .setTrustedServerCertificateChain(caCertificateInputStream)
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
                    .build();
        }

        final TestMetricsListener metricsListener = new TestMetricsListener();
        unconnectedClient.setMetricsListener(metricsListener);

        this.server.shutdown().await();

        final Future<Void> connectionFuture = unconnectedClient.connect(HOST, PORT);
        connectionFuture.await();

        metricsListener.waitForNonZeroFailedConnections();

        assertFalse(connectionFuture.isSuccess());
        assertEquals(1, metricsListener.getConnectionAttemptsStarted().get());
        assertEquals(1, metricsListener.getFailedConnectionAttempts().get());
        assertEquals(0, metricsListener.getSuccessfulConnectionAttempts().get());
    }

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        final SecureRandom random = SecureRandom.getInstance("SHA1PRNG");

        keyPairGenerator.initialize(256, random);

        return keyPairGenerator.generateKeyPair();
    }

    private static String generateRandomToken() {
        final byte[] tokenBytes = new byte[TOKEN_LENGTH];
        new Random().nextBytes(tokenBytes);

        final StringBuilder builder = new StringBuilder(TOKEN_LENGTH * 2);

        for (final byte b : tokenBytes) {
            builder.append(String.format("%02x", b));
        }

        return builder.toString();
    }

    private static String generateRandomPayload() {
        final ApnsPayloadBuilder payloadBuilder = new ApnsPayloadBuilder();
        payloadBuilder.setAlertBody(UUID.randomUUID().toString());

        return payloadBuilder.buildWithDefaultMaximumLength();
    }
}
