/*
 * Copyright (c) 2013-2017 Turo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.turo.pushy.apns;

import com.turo.pushy.apns.auth.ApnsSigningKey;
import com.turo.pushy.apns.auth.ApnsVerificationKey;
import com.turo.pushy.apns.util.ApnsPayloadBuilder;
import com.turo.pushy.apns.util.SimpleApnsPushNotification;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.*;
import org.junit.runner.RunWith;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitParamsRunner.class)
public class ApnsClientTest {

    private static NioEventLoopGroup EVENT_LOOP_GROUP;

    private static final String CA_CERTIFICATE_FILENAME = "/ca.pem";
    private static final String SERVER_CERTIFICATES_FILENAME = "/server-certs.pem";
    private static final String SERVER_KEY_FILENAME = "/server-key.pem";

    private static final String MULTI_TOPIC_CLIENT_KEYSTORE_FILENAME = "/multi-topic-client.p12";
    private static final String KEYSTORE_PASSWORD = "pushy-test";

    private static File CA_CERTIFICATE;

    private static final String HOST = "localhost";
    private static final int PORT = 8443;

    private static final String DEFAULT_TEAM_ID = "team-id";
    private static final String DEFAULT_KEY_ID = "key-id";
    private static final String DEFAULT_TOPIC = "com.relayrides.pushy";
    private static final String DEFAULT_DEVICE_TOKEN = generateRandomDeviceToken();

    private static final int TOKEN_LENGTH = 32; // bytes

    private ApnsSigningKey signingKey;
    private ApnsVerificationKey verificationKey;

    private MockApnsServer server;
    private ApnsClient tokenAuthenticationClient;
    private ApnsClient tlsAuthenticationClient;

    private TestMetricsListener tokenAuthenticationMetricsListener;

    private static class TestMetricsListener implements ApnsClientMetricsListener {

        private final List<Long> writeFailures = new ArrayList<>();
        private final List<Long> sentNotifications = new ArrayList<>();
        private final List<Long> acceptedNotifications = new ArrayList<>();
        private final List<Long> rejectedNotifications = new ArrayList<>();

        private final AtomicInteger connectionsAdded = new AtomicInteger(0);
        private final AtomicInteger connectionsRemoved = new AtomicInteger(0);
        private final AtomicInteger failedConnectionAttempts = new AtomicInteger(0);

        @Override
        public void handleWriteFailure(final ApnsClient apnsClient, final long notificationId) {
            synchronized (this.writeFailures) {
                this.writeFailures.add(notificationId);
                this.writeFailures.notifyAll();
            }
        }

        @Override
        public void handleNotificationSent(final ApnsClient apnsClient, final long notificationId) {
            this.sentNotifications.add(notificationId);
        }

        @Override
        public void handleNotificationAccepted(final ApnsClient apnsClient, final long notificationId) {
            synchronized (this.acceptedNotifications) {
                this.acceptedNotifications.add(notificationId);
                this.acceptedNotifications.notifyAll();
            }
        }

        @Override
        public void handleNotificationRejected(final ApnsClient apnsClient, final long notificationId) {
            synchronized (this.rejectedNotifications) {
                this.rejectedNotifications.add(notificationId);
                this.rejectedNotifications.notifyAll();
            }
        }

        @Override
        public void handleConnectionAdded(final ApnsClient apnsClient) {
            synchronized (this.connectionsAdded) {
                this.connectionsAdded.incrementAndGet();
                this.connectionsAdded.notifyAll();
            }
        }

        @Override
        public void handleConnectionRemoved(final ApnsClient apnsClient) {
            synchronized (this.connectionsRemoved) {
                this.connectionsRemoved.incrementAndGet();
                this.connectionsRemoved.notifyAll();
            }
        }

        @Override
        public void handleConnectionCreationFailed(final ApnsClient apnsClient) {
            synchronized (this.failedConnectionAttempts) {
                this.failedConnectionAttempts.incrementAndGet();
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
            synchronized (this.connectionsRemoved) {
                while (this.connectionsRemoved.get() == 0) {
                    this.connectionsRemoved.wait();
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

        public AtomicInteger getConnectionsAdded() {
            return this.connectionsAdded;
        }

        public AtomicInteger getConnectionsRemoved() {
            return this.connectionsRemoved;
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

        CA_CERTIFICATE = new File(ApnsClientTest.class.getResource(CA_CERTIFICATE_FILENAME).toURI());
    }

    @Before
    public void setUp() throws Exception {
        final KeyPair keyPair = KeyPairUtil.generateKeyPair();

        this.signingKey = new ApnsSigningKey(DEFAULT_KEY_ID, DEFAULT_TEAM_ID, (ECPrivateKey) keyPair.getPrivate());
        this.verificationKey = new ApnsVerificationKey(DEFAULT_KEY_ID, DEFAULT_TEAM_ID, (ECPublicKey) keyPair.getPublic());

        this.server = new MockApnsServerBuilder()
                .setServerCredentials(ApnsClientTest.class.getResourceAsStream(SERVER_CERTIFICATES_FILENAME), ApnsClientTest.class.getResourceAsStream(SERVER_KEY_FILENAME), null)
                .setTrustedClientCertificateChain(CA_CERTIFICATE)
                .setEventLoopGroup(EVENT_LOOP_GROUP)
                .build();

        this.server.registerVerificationKey(this.verificationKey, DEFAULT_TOPIC);
        this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, DEFAULT_DEVICE_TOKEN, null);

        this.server.start(PORT).await();

        this.tokenAuthenticationMetricsListener = new TestMetricsListener();

        this.tokenAuthenticationClient = new ApnsClientBuilder()
                .setApnsServer(HOST, PORT)
                .setTrustedServerCertificateChain(CA_CERTIFICATE)
                .setSigningKey(this.signingKey)
                .setEventLoopGroup(EVENT_LOOP_GROUP)
                .setMetricsListener(this.tokenAuthenticationMetricsListener)
                .build();

        try (final InputStream p12InputStream = ApnsClientTest.class.getResourceAsStream(MULTI_TOPIC_CLIENT_KEYSTORE_FILENAME)) {
            this.tlsAuthenticationClient = new ApnsClientBuilder()
                    .setApnsServer(HOST, PORT)
                    .setClientCredentials(p12InputStream, KEYSTORE_PASSWORD)
                    .setTrustedServerCertificateChain(CA_CERTIFICATE)
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
                    .build();
        }
    }

    @After
    public void tearDown() throws Exception {
        this.tokenAuthenticationClient.close();
        this.tlsAuthenticationClient.close();

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
        final ApnsClient managedGroupClient = new ApnsClientBuilder()
                .setApnsServer(HOST, PORT)
                .setSigningKey(this.signingKey)
                .setTrustedServerCertificateChain(CA_CERTIFICATE)
                .build();

        assertTrue(managedGroupClient.close().await().isSuccess());
    }

    @Test
    public void testConnectToUntrustedServer() throws Exception {
        final ApnsClient cautiousClient = new ApnsClientBuilder()
                .setApnsServer(HOST, PORT)
                .setSigningKey(this.signingKey)
                .setEventLoopGroup(EVENT_LOOP_GROUP)
                .build();

        try {
            final Future<PushNotificationResponse<SimpleApnsPushNotification>> sendFuture =
                    cautiousClient.sendNotification(new SimpleApnsPushNotification("test", "test", "test")).await();

            assertFalse(sendFuture.isSuccess());
            assertTrue(sendFuture.cause() instanceof SSLHandshakeException);
        } finally {
            cautiousClient.close().await();
        }
    }

    @Test
    public void testReconnectionAfterClose() throws Exception {
        this.tokenAuthenticationClient.close().await();

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(DEFAULT_DEVICE_TOKEN, DEFAULT_TOPIC, generateRandomPayload());

        final Future<PushNotificationResponse<SimpleApnsPushNotification>> sendFuture =
                this.tokenAuthenticationClient.sendNotification(pushNotification).await();

        assertFalse(sendFuture.isSuccess());
    }

    @Test
    @Parameters({"true", "false"})
    public void testSendNotification(final boolean useTokenAuthentication) throws Exception {
        final String testToken = ApnsClientTest.generateRandomDeviceToken();

        this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, testToken, null);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, DEFAULT_TOPIC, "test-payload");

        final ApnsClient client = useTokenAuthentication ? this.tokenAuthenticationClient : this.tlsAuthenticationClient;

        final PushNotificationResponse<SimpleApnsPushNotification> response =
                client.sendNotification(pushNotification).get();

        assertTrue(response.isAccepted());
    }

    @Test
    public void testSendNotificationWithExpiredAuthenticationToken() throws Exception {
        this.server.shutdown().await();

        final MockApnsServer expiredTokenServer = new MockApnsServerBuilder()
                .setServerCredentials(ApnsClientTest.class.getResourceAsStream(SERVER_CERTIFICATES_FILENAME), ApnsClientTest.class.getResourceAsStream(SERVER_KEY_FILENAME), null)
                .setEventLoopGroup(EVENT_LOOP_GROUP)
                .setEmulateExpiredFirstToken(true)
                .build();

        try {
            expiredTokenServer.registerVerificationKey(this.verificationKey, DEFAULT_TOPIC);

            assertTrue(expiredTokenServer.start(PORT).await().isSuccess());

            final String testToken = ApnsClientTest.generateRandomDeviceToken();
            expiredTokenServer.registerDeviceTokenForTopic(DEFAULT_TOPIC, testToken, null);

            final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, DEFAULT_TOPIC, "test-payload");
            final PushNotificationResponse<SimpleApnsPushNotification> response =
                    this.tokenAuthenticationClient.sendNotification(pushNotification).get();

            assertTrue(response.isAccepted());
            this.tokenAuthenticationMetricsListener.waitForNonZeroAcceptedNotifications();

            // See https://github.com/relayrides/pushy/issues/448
            assertEquals(1, this.tokenAuthenticationMetricsListener.getSentNotifications().size());
            assertEquals(1, this.tokenAuthenticationMetricsListener.getAcceptedNotifications().size());
            assertTrue(this.tokenAuthenticationMetricsListener.getRejectedNotifications().isEmpty());
        } finally {
            expiredTokenServer.shutdown().await();
        }
    }

    @Test
    @Parameters({"true", "false"})
    public void testSendManyNotifications(final boolean useTokenAuthentication) throws Exception {
        final int notificationCount = 1000;

        final List<SimpleApnsPushNotification> pushNotifications = new ArrayList<>();

        for (int i = 0; i < notificationCount; i++) {
            final String token = ApnsClientTest.generateRandomDeviceToken();
            final String payload = ApnsClientTest.generateRandomPayload();

            this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, token, null);
            pushNotifications.add(new SimpleApnsPushNotification(token, DEFAULT_TOPIC, payload));
        }

        final List<Future<PushNotificationResponse<SimpleApnsPushNotification>>> futures = new ArrayList<>();

        final ApnsClient client = useTokenAuthentication ? this.tokenAuthenticationClient : this.tlsAuthenticationClient;

        for (final SimpleApnsPushNotification pushNotification : pushNotifications) {
            futures.add(client.sendNotification(pushNotification));
        }

        for (final Future<PushNotificationResponse<SimpleApnsPushNotification>> future : futures) {
            future.await();

            assertTrue("Send future should have succeeded, but failed with: " + future.cause(), future.isSuccess());
            assertTrue(future.get().isAccepted());
        }
    }

    @Test
    @Parameters({"true", "false"})
    public void testSendManyNotificationsWithListeners(final boolean useTokenAuthentication) throws Exception {
        final int notificationCount = 1000;

        final List<SimpleApnsPushNotification> pushNotifications = new ArrayList<>();

        for (int i = 0; i < notificationCount; i++) {
            final String token = ApnsClientTest.generateRandomDeviceToken();
            final String payload = ApnsClientTest.generateRandomPayload();

            this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, token, null);
            pushNotifications.add(new SimpleApnsPushNotification(token, DEFAULT_TOPIC, payload));
        }

        final ApnsClient client = useTokenAuthentication ? this.tokenAuthenticationClient : this.tlsAuthenticationClient;
        final CountDownLatch countDownLatch = new CountDownLatch(notificationCount);

        for (final SimpleApnsPushNotification pushNotification : pushNotifications) {
            final Future<PushNotificationResponse<SimpleApnsPushNotification>> future =
                    client.sendNotification(pushNotification);

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
                ApnsClientTest.generateRandomDeviceToken(), DEFAULT_TOPIC, ApnsClientTest.generateRandomPayload());

        final CountDownLatch countDownLatch = new CountDownLatch(notificationCount);

        for (int i = 0; i < notificationCount; i++) {
            final Future<PushNotificationResponse<SimpleApnsPushNotification>> future =
                    this.tokenAuthenticationClient.sendNotification(pushNotification);

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
    public void testSendNotificationWithExpiredDeviceToken() throws Exception {
        final String testToken = ApnsClientTest.generateRandomDeviceToken();
        final Date now = new Date();

        this.server.registerDeviceTokenForTopic(DEFAULT_TOPIC, testToken, now);

        final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(testToken, DEFAULT_TOPIC, "test-payload");
        final PushNotificationResponse<SimpleApnsPushNotification> response =
                this.tokenAuthenticationClient.sendNotification(pushNotification).get();

        assertFalse(response.isAccepted());
        assertEquals("Unregistered", response.getRejectionReason());
        assertEquals(now, response.getTokenInvalidationTimestamp());
    }

    @Test
    public void testSendNotificationWithInternalServerError() throws Exception {
        // Shut down the "normal" server to free the port
        this.tearDown();

        final MockApnsServer terribleTerribleServer = new MockApnsServerBuilder()
                .setServerCredentials(ApnsClientTest.class.getResourceAsStream(SERVER_CERTIFICATES_FILENAME), ApnsClientTest.class.getResourceAsStream(SERVER_KEY_FILENAME), null)
                .setEventLoopGroup(EVENT_LOOP_GROUP)
                .setEmulateInternalErrors(true)
                .build();

        try {
            terribleTerribleServer.registerVerificationKey(this.verificationKey, DEFAULT_TOPIC);

            final ApnsClient unfortunateClient = new ApnsClientBuilder()
                    .setApnsServer(HOST, PORT)
                    .setSigningKey(this.signingKey)
                    .setTrustedServerCertificateChain(CA_CERTIFICATE)
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
                    .build();

            try {
                terribleTerribleServer.start(PORT).await();

                final SimpleApnsPushNotification pushNotification =
                        new SimpleApnsPushNotification(ApnsClientTest.generateRandomDeviceToken(), DEFAULT_TOPIC, "test-payload");

                final Future<PushNotificationResponse<SimpleApnsPushNotification>> future =
                        unfortunateClient.sendNotification(pushNotification).await();

                assertTrue(future.isDone());
                assertFalse(future.isSuccess());
                assertTrue(future.cause() instanceof ApnsServerException);
            } finally {
                unfortunateClient.close().await();
                Thread.sleep(10);
            }
        } finally {
            terribleTerribleServer.shutdown().await();
        }
    }

    @Test
    public void testAcceptedNotificationAndAddedConnectionMetrics() throws Exception {
        this.testSendNotification(true);
        this.tokenAuthenticationMetricsListener.waitForNonZeroAcceptedNotifications();

        assertEquals(1, this.tokenAuthenticationMetricsListener.getSentNotifications().size());
        assertEquals(this.tokenAuthenticationMetricsListener.getSentNotifications(), this.tokenAuthenticationMetricsListener.getAcceptedNotifications());
        assertTrue(this.tokenAuthenticationMetricsListener.getRejectedNotifications().isEmpty());

        assertEquals(1, this.tokenAuthenticationMetricsListener.getConnectionsAdded().get());
        assertEquals(0, this.tokenAuthenticationMetricsListener.getConnectionsRemoved().get());
        assertEquals(0, this.tokenAuthenticationMetricsListener.getFailedConnectionAttempts().get());
    }

    @Test
    public void testRejectedNotificationMetrics() throws Exception {
        this.testSendNotificationWithExpiredDeviceToken();
        this.tokenAuthenticationMetricsListener.waitForNonZeroRejectedNotifications();

        assertEquals(1, this.tokenAuthenticationMetricsListener.getSentNotifications().size());
        assertEquals(this.tokenAuthenticationMetricsListener.getSentNotifications(), this.tokenAuthenticationMetricsListener.getRejectedNotifications());
        assertTrue(this.tokenAuthenticationMetricsListener.getAcceptedNotifications().isEmpty());
    }

    @Test
    public void testFailedConnectionAndWriteFailureMetrics() throws Exception {
        this.server.shutdown().await();

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(generateRandomDeviceToken(), DEFAULT_TOPIC, generateRandomPayload());

        this.tokenAuthenticationClient.sendNotification(pushNotification).await();

        this.tokenAuthenticationMetricsListener.waitForNonZeroFailedConnections();
        this.tokenAuthenticationMetricsListener.waitForNonZeroWriteFailures();

        assertEquals(0, this.tokenAuthenticationMetricsListener.getConnectionsAdded().get());
        assertEquals(0, this.tokenAuthenticationMetricsListener.getConnectionsRemoved().get());
        assertEquals(1, this.tokenAuthenticationMetricsListener.getFailedConnectionAttempts().get());

        assertEquals(1, this.tokenAuthenticationMetricsListener.getWriteFailures().size());
    }

    private static String generateRandomDeviceToken() {
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
