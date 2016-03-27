package com.relayrides.pushy.apns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;

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
import io.netty.util.concurrent.GenericFutureListener;

public class ApnsClientTest {

    private static NioEventLoopGroup EVENT_LOOP_GROUP;

    private static final String SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME = "/single-topic-client.p12";
    private static final String SINGLE_TOPIC_CLIENT_KEYSTORE_UNPROTECTED_FILENAME = "/single-topic-client-unprotected.p12";
    private static final String MULTI_TOPIC_CLIENT_KEYSTORE_FILENAME = "/multi-topic-client.p12";
    private static final String UNTRUSTED_CLIENT_KEYSTORE_FILENAME = "/untrusted-client.p12";

    private static File CA_CERTIFICATE;

    private static final String KEYSTORE_PASSWORD = "pushy-test";

    private static final String HOST = "localhost";
    private static final int PORT = 8443;

    private static final String DEFAULT_TOPIC = "com.relayrides.pushy";

    private static final int TOKEN_LENGTH = 32; // bytes

    private MockApnsServer server;
    private ApnsClient<SimpleApnsPushNotification> client;

    @Rule
    public Timeout globalTimeout = new Timeout(30000);

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

        CA_CERTIFICATE = new File(ApnsClientTest.class.getResource("/ca.pem").toURI());
    }

    @Before
    public void setUp() throws Exception {
        this.server = new MockApnsServer(EVENT_LOOP_GROUP);
        this.server.start(PORT).await();

        this.client = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME, KEYSTORE_PASSWORD),
                EVENT_LOOP_GROUP);

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
    public void testApnsClientWithPasswordProtectedP12File() throws Exception {
        // We're happy here as long as nothing throws an exception
        new ApnsClient<SimpleApnsPushNotification>(
                new File(ApnsClientTest.class.getResource(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME).toURI()),
                KEYSTORE_PASSWORD);
    }

    @Test
    public void testApnsClientWithPasswordProtectedP12InputStream() throws Exception {
        // We're happy here as long as nothing throws an exception
        try (final InputStream p12InputStream = ApnsClientTest.class.getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME)) {
            new ApnsClient<SimpleApnsPushNotification>(p12InputStream, KEYSTORE_PASSWORD);
        }
    }

    // TODO Add a test for completely unprotected P12 files (i.e. with a null password) if it turns out it's actually
    // possible to create such a thing (signs point to "no" right now)

    @Test
    public void testApnsClientWithCertificateAndPasswordProtectedKey() throws Exception {
        // We're happy here as long as nothing throws an exception
        try (final InputStream p12InputStream = ApnsClientTest.class.getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME)) {
            final PrivateKeyEntry privateKeyEntry =
                    P12Util.getPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            new ApnsClient<SimpleApnsPushNotification>(
                    (X509Certificate) privateKeyEntry.getCertificate(), privateKeyEntry.getPrivateKey(), KEYSTORE_PASSWORD);
        }
    }

    @Test
    public void testApnsClientWithCertificateAndUnprotectedKey() throws Exception {
        // We DO need a password to unlock the keystore, but the key itself should be unprotected
        try (final InputStream p12InputStream = ApnsClientTest.class.getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_UNPROTECTED_FILENAME)) {

            final PrivateKeyEntry privateKeyEntry =
                    P12Util.getPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            new ApnsClient<SimpleApnsPushNotification>(
                    (X509Certificate) privateKeyEntry.getCertificate(), privateKeyEntry.getPrivateKey(), null);
        }
    }

    @Test
    public void testApnsClientWithManagedEventLoopGroup() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> managedGroupClient = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME, KEYSTORE_PASSWORD), null);

        assertTrue(managedGroupClient.connect(HOST, PORT).await().isSuccess());
        assertTrue(managedGroupClient.disconnect().await().isSuccess());
    }

    @Test
    public void testRestartApnsClientWithManagedEventLoopGroup() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> managedGroupClient = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME, KEYSTORE_PASSWORD), null);

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
        final ApnsClient<SimpleApnsPushNotification> unconnectedClient = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME, KEYSTORE_PASSWORD),
                EVENT_LOOP_GROUP);

        final Future<Void> reconnectionFuture = unconnectedClient.getReconnectionFuture();

        reconnectionFuture.await();

        assertFalse(unconnectedClient.isConnected());
        assertFalse(reconnectionFuture.isSuccess());
    }

    @Test
    public void testConnectWithUntrustedCertificate() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> untrustedClient = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(UNTRUSTED_CLIENT_KEYSTORE_FILENAME, KEYSTORE_PASSWORD),
                EVENT_LOOP_GROUP);

        final Future<Void> connectFuture = untrustedClient.connect(HOST, PORT).await();
        assertFalse(connectFuture.isSuccess());

        untrustedClient.disconnect().await();
    }

    @Test
    public void testSendNotificationBeforeConnected() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> unconnectedClient = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME, KEYSTORE_PASSWORD),
                EVENT_LOOP_GROUP);

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

        final List<SimpleApnsPushNotification> pushNotifications = new ArrayList<>();

        for (int i = 0; i < notificationCount; i++) {
            final String token = ApnsClientTest.generateRandomToken();
            final String payload = ApnsClientTest.generateRandomPayload();

            this.server.registerToken(DEFAULT_TOPIC, token);
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

            this.server.registerToken(DEFAULT_TOPIC, token);
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
        final ApnsClient<SimpleApnsPushNotification> multiTopicClient = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(MULTI_TOPIC_CLIENT_KEYSTORE_FILENAME, KEYSTORE_PASSWORD),
                EVENT_LOOP_GROUP);

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
        final ApnsClient<SimpleApnsPushNotification> multiTopicClient = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(MULTI_TOPIC_CLIENT_KEYSTORE_FILENAME, KEYSTORE_PASSWORD),
                EVENT_LOOP_GROUP);

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

        final Date now = new Date();

        this.server.registerToken(DEFAULT_TOPIC, testToken, now);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, null, "test-payload");
        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.client.sendNotification(pushNotification).get();

        assertFalse(response.isAccepted());
        assertEquals("Unregistered", response.getRejectionReason());
        assertEquals(now, response.getTokenInvalidationTimestamp());
    }

    @Test
    public void testWriteFailureMetrics() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> unconnectedClient = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME, KEYSTORE_PASSWORD),
                EVENT_LOOP_GROUP);

        final TestMetricsListener metricsListener = new TestMetricsListener();
        unconnectedClient.setMetricsListener(metricsListener);

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(ApnsClientTest.generateRandomToken(), null, ApnsClientTest.generateRandomPayload());

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

        this.testSendNotificationWithBadTopic();
        metricsListener.waitForNonZeroRejectedNotifications();

        assertEquals(1, metricsListener.getSentNotifications().size());
        assertEquals(metricsListener.getSentNotifications(), metricsListener.getRejectedNotifications());
        assertTrue(metricsListener.getAcceptedNotifications().isEmpty());
    }

    @Test
    public void testSuccessfulConnectionMetrics() throws Exception {
        final ApnsClient<SimpleApnsPushNotification> unconnectedClient = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME, KEYSTORE_PASSWORD),
                EVENT_LOOP_GROUP);

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
        final ApnsClient<SimpleApnsPushNotification> unconnectedClient = new ApnsClient<>(
                ApnsClientTest.getSslContextForTestClient(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME, KEYSTORE_PASSWORD),
                EVENT_LOOP_GROUP);

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

    private static SslContext getSslContextForTestClient(final String p12Filename, final String password) throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException, UnrecoverableEntryException {
        try (final InputStream p12InputStream = ApnsClientTest.class.getResourceAsStream(p12Filename)) {
            final PrivateKeyEntry privateKeyEntry = P12Util.getPrivateKeyEntryFromP12InputStream(p12InputStream, password);

            return SslContextBuilder.forClient()
                    .sslProvider(OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .keyManager(privateKeyEntry.getPrivateKey(), (X509Certificate) privateKeyEntry.getCertificate())
                    .trustManager(CA_CERTIFICATE)
                    .applicationProtocolConfig(new ApplicationProtocolConfig(Protocol.ALPN,
                            SelectorFailureBehavior.NO_ADVERTISE,
                            SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2))
                    .build();
        }
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
