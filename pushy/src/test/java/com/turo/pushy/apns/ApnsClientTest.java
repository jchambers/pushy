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
import com.turo.pushy.apns.auth.KeyPairUtil;
import com.turo.pushy.apns.server.*;
import com.turo.pushy.apns.util.ApnsPayloadBuilder;
import com.turo.pushy.apns.util.SimpleApnsPushNotification;
import io.netty.buffer.ByteBuf;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(JUnitParamsRunner.class)
public class ApnsClientTest {

    private static NioEventLoopGroup EVENT_LOOP_GROUP;

    private static final String CA_CERTIFICATE_FILENAME = "/ca.pem";
    private static final String SERVER_CERTIFICATES_FILENAME = "/server-certs.pem";
    private static final String SERVER_KEY_FILENAME = "/server-key.pem";

    private static final String MULTI_TOPIC_CLIENT_KEYSTORE_FILENAME = "/multi-topic-client.p12";
    private static final String KEYSTORE_PASSWORD = "pushy-test";

    private static final String HOST = "localhost";
    private static final int PORT = 8443;

    private static final String TEAM_ID = "team-id";
    private static final String KEY_ID = "key-id";
    private static final String TOPIC = "com.relayrides.pushy";
    private static final String DEVICE_TOKEN = generateRandomDeviceToken();
    private static final String PAYLOAD = generateRandomPayload();

    private static final Map<String, Set<String>> DEVICE_TOKENS_BY_TOPIC =
            Collections.singletonMap(TOPIC, Collections.singleton(DEVICE_TOKEN));

    private static final Map<String, Date> EXPIRATION_TIMESTAMPS_BY_DEVICE_TOKEN = Collections.emptyMap();

    private static final int TOKEN_LENGTH = 32; // bytes

    private ApnsSigningKey signingKey;
    private Map<String, ApnsVerificationKey> verificationKeysByKeyId;
    private Map<ApnsVerificationKey, Set<String>> topicsByVerificationKey;

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

        void waitForNonZeroWriteFailures() throws InterruptedException {
            synchronized (this.writeFailures) {
                while (this.writeFailures.isEmpty()) {
                    this.writeFailures.wait();
                }
            }
        }

        void waitForNonZeroAcceptedNotifications() throws InterruptedException {
            synchronized (this.acceptedNotifications) {
                while (this.acceptedNotifications.isEmpty()) {
                    this.acceptedNotifications.wait();
                }
            }
        }

        void waitForNonZeroRejectedNotifications() throws InterruptedException {
            synchronized (this.rejectedNotifications) {
                while (this.rejectedNotifications.isEmpty()) {
                    this.rejectedNotifications.wait();
                }
            }
        }

        void waitForNonZeroFailedConnections() throws InterruptedException {
            synchronized (this.failedConnectionAttempts) {
                while (this.failedConnectionAttempts.get() == 0) {
                    this.failedConnectionAttempts.wait();
                }
            }
        }

        List<Long> getWriteFailures() {
            return this.writeFailures;
        }

        List<Long> getSentNotifications() {
            return this.sentNotifications;
        }

        List<Long> getAcceptedNotifications() {
            return this.acceptedNotifications;
        }

        List<Long> getRejectedNotifications() {
            return this.rejectedNotifications;
        }

        AtomicInteger getConnectionsAdded() {
            return this.connectionsAdded;
        }

        AtomicInteger getConnectionsRemoved() {
            return this.connectionsRemoved;
        }

        AtomicInteger getFailedConnectionAttempts() {
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
        final KeyPair keyPair = KeyPairUtil.generateKeyPair();

        this.signingKey = new ApnsSigningKey(KEY_ID, TEAM_ID, (ECPrivateKey) keyPair.getPrivate());
        final ApnsVerificationKey verificationKey =
                new ApnsVerificationKey(KEY_ID, TEAM_ID, (ECPublicKey) keyPair.getPublic());

        this.verificationKeysByKeyId = Collections.singletonMap(KEY_ID, verificationKey);
        this.topicsByVerificationKey = Collections.singletonMap(verificationKey, Collections.singleton(TOPIC));
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
                .build();

        assertTrue(managedGroupClient.close().await().isSuccess());
    }

    @Test
    @Parameters({"true", "false"})
    public void testSendNotificationToUntrustedServer(final boolean useTokenAuthentication) throws Exception {
        final ApnsClient cautiousClient;

        if (useTokenAuthentication) {
            cautiousClient = new ApnsClientBuilder()
                    .setApnsServer(HOST, PORT)
                    .setSigningKey(this.signingKey)
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
                    .build();
        } else {
            try (final InputStream p12InputStream = getClass().getResourceAsStream(MULTI_TOPIC_CLIENT_KEYSTORE_FILENAME)) {
                cautiousClient = new ApnsClientBuilder()
                        .setApnsServer(HOST, PORT)
                        .setClientCredentials(p12InputStream, KEYSTORE_PASSWORD)
                        .setEventLoopGroup(EVENT_LOOP_GROUP)
                        .build();
            }
        }

        final MockApnsServer server = this.buildServer(new AcceptAllPushNotificationHandlerFactory());

        try {
            server.start(PORT).await();

            final Future<PushNotificationResponse<SimpleApnsPushNotification>> sendFuture =
                    cautiousClient.sendNotification(
                            new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD)).await();

            assertFalse("Clients must not connect to untrusted servers.",
                    sendFuture.isSuccess());

            assertTrue("Clients should refuse to connect to untrusted servers due to an SSL handshake failure.",
                    sendFuture.cause() instanceof SSLHandshakeException);
        } finally {
            cautiousClient.close().await();
            server.shutdown().await();
        }
    }

    @Test
    @Parameters({"true", "false"})
    public void testRepeatedClose(final boolean useTokenAuthentication) throws Exception {
        final ApnsClient client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient() : this.buildTlsAuthenticationClient();

        try {
            assertTrue("Client should close successfully under normal circumstances.",
                    client.close().await().isSuccess());

            assertTrue("Client should report successful closure on subsequent calls to close().",
                    client.close().await().isSuccess());
        } finally {
            client.close().await();
        }
    }

    @Test
    @Parameters({"true", "false"})
    public void testSendNotificationAfterClose(final boolean useTokenAuthentication) throws Exception {
        final MockApnsServer server = this.buildServer(new AcceptAllPushNotificationHandlerFactory());
        final ApnsClient client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient() : this.buildTlsAuthenticationClient();

        try {
            server.start(PORT).await();
            client.close().await();

            final SimpleApnsPushNotification pushNotification =
                    new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            final Future<PushNotificationResponse<SimpleApnsPushNotification>> sendFuture =
                    client.sendNotification(pushNotification).await();

            assertFalse("Once a client has closed, attempts to send push notifications should fail.",
                    sendFuture.isSuccess());
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    @Test
    @Parameters({"true", "false"})
    public void testSendNotification(final boolean useTokenAuthentication) throws Exception {
        final ValidatingPushNotificationHandlerFactory handlerFactory = new ValidatingPushNotificationHandlerFactory(
                DEVICE_TOKENS_BY_TOPIC, EXPIRATION_TIMESTAMPS_BY_DEVICE_TOKEN, this.verificationKeysByKeyId,
                this.topicsByVerificationKey);

        final MockApnsServer server = this.buildServer(handlerFactory);
        final ApnsClient<SimpleApnsPushNotification> client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient() : this.buildTlsAuthenticationClient();

        try {
            server.start(PORT).await();

            final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            final PushNotificationResponse<SimpleApnsPushNotification> response =
                    client.sendNotification(pushNotification).get();

            assertTrue("Clients must send notifications that conform to the APNs protocol specification.",
                    response.isAccepted());
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    @Test
    public void testSendNotificationWithExpiredAuthenticationToken() throws Exception {
        final PushNotificationHandlerFactory expireFirstTokenHandlerFactory = new PushNotificationHandlerFactory() {
            @Override
            public PushNotificationHandler buildHandler(final SSLSession sslSession) {
                return new ExpireFirstTokenPushNotificationHandler();
            }
        };

        final MockApnsServer server = this.buildServer(expireFirstTokenHandlerFactory);

        final TestMetricsListener metricsListener = new TestMetricsListener();
        final ApnsClient<SimpleApnsPushNotification> client = this.buildTokenAuthenticationClient(metricsListener);

        try {
            server.start(PORT).await();

            final SimpleApnsPushNotification pushNotification =
                    new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            final PushNotificationResponse<SimpleApnsPushNotification> response =
                    client.sendNotification(pushNotification).get();

            assertTrue("Client should automatically re-send notifications with expired authentication tokens.",
                    response.isAccepted());

            metricsListener.waitForNonZeroAcceptedNotifications();

            // See https://github.com/relayrides/pushy/issues/448
            assertEquals("Re-sent notifications with expired tokens must not be double-counted.",
                    1, metricsListener.getSentNotifications().size());

            assertEquals("Re-sent notifications should be counted as accepted exactly once.",
                    1, metricsListener.getAcceptedNotifications().size());

            assertTrue("Notifications with expired authentication tokens should not count as rejections.",
                    metricsListener.getRejectedNotifications().isEmpty());
        } finally {
            client.close().await();
            server.shutdown().await();
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

            pushNotifications.add(new SimpleApnsPushNotification(token, TOPIC, payload));
        }

        final List<Future<PushNotificationResponse<SimpleApnsPushNotification>>> futures = new ArrayList<>();

        final MockApnsServer server = this.buildServer(new AcceptAllPushNotificationHandlerFactory());
        final ApnsClient client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient() : this.buildTlsAuthenticationClient();

        try {
            server.start(PORT).await();

            for (final SimpleApnsPushNotification pushNotification : pushNotifications) {
                futures.add(client.sendNotification(pushNotification));
            }

            for (final Future<PushNotificationResponse<SimpleApnsPushNotification>> future : futures) {
                future.await();

                assertTrue("Send future should have succeeded, but failed with: " + future.cause(), future.isSuccess());
            }
        } finally {
            client.close().await();
            server.shutdown().await();
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

            pushNotifications.add(new SimpleApnsPushNotification(token, TOPIC, payload));
        }

        final MockApnsServer server = this.buildServer(new AcceptAllPushNotificationHandlerFactory());
        final ApnsClient client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient() : this.buildTlsAuthenticationClient();

        final CountDownLatch countDownLatch = new CountDownLatch(notificationCount);

        try {
            server.start(PORT).await();

            for (final SimpleApnsPushNotification pushNotification : pushNotifications) {
                final Future<PushNotificationResponse<SimpleApnsPushNotification>> future =
                        client.sendNotification(pushNotification);

                future.addListener(new GenericFutureListener<Future<PushNotificationResponse<SimpleApnsPushNotification>>>() {

                    @Override
                    public void operationComplete(final Future<PushNotificationResponse<SimpleApnsPushNotification>> future) throws Exception {
                        if (future.isSuccess()) {
                            countDownLatch.countDown();
                        }
                    }
                });
            }

            countDownLatch.await();
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    // See https://github.com/relayrides/pushy/issues/256
    @Test
    @Parameters({"true", "false"})
    public void testRepeatedlySendSameNotification(final boolean useTokenAuthentication) throws Exception {
        final int notificationCount = 1000;

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

        final CountDownLatch countDownLatch = new CountDownLatch(notificationCount);

        final MockApnsServer server = this.buildServer(new AcceptAllPushNotificationHandlerFactory());
        final ApnsClient client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient() : this.buildTlsAuthenticationClient();

        try {
            server.start(PORT).await();

            for (int i = 0; i < notificationCount; i++) {
                final Future<PushNotificationResponse<SimpleApnsPushNotification>> future =
                        client.sendNotification(pushNotification);

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
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    @Test
    @Parameters({"true", "false"})
    public void testSendNotificationWithExpiredDeviceToken(final boolean useTokenAuthentication) throws Exception {
        final Date expiration = new Date();

        final PushNotificationHandlerFactory handlerFactory = new PushNotificationHandlerFactory() {
            @Override
            public PushNotificationHandler buildHandler(final SSLSession sslSession) {
                return new PushNotificationHandler() {
                    @Override
                    public void handlePushNotification(final Http2Headers headers, final ByteBuf payload) throws RejectedNotificationException {
                        throw new UnregisteredDeviceTokenException(expiration, null);
                    }
                };
            }
        };

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

        final MockApnsServer server = this.buildServer(handlerFactory);
        final ApnsClient<SimpleApnsPushNotification> client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient() : this.buildTlsAuthenticationClient();

        try {
            server.start(PORT).await();

            final PushNotificationResponse<SimpleApnsPushNotification> response =
                    client.sendNotification(pushNotification).get();

            assertFalse(response.isAccepted());
            assertEquals("Unregistered", response.getRejectionReason());
            assertEquals(expiration, response.getTokenInvalidationTimestamp());
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    @Test
    @Parameters({"true", "false"})
    public void testSendNotificationWithInternalServerError(final boolean useTokenAuthentication) throws Exception {
        final PushNotificationHandlerFactory handlerFactory = new PushNotificationHandlerFactory() {
            @Override
            public PushNotificationHandler buildHandler(final SSLSession sslSession) {
                return new PushNotificationHandler() {
                    @Override
                    public void handlePushNotification(final Http2Headers headers, final ByteBuf payload) {
                        throw new RuntimeException("I am a terrible server.");
                    }
                };
            }
        };

        final MockApnsServer server = this.buildServer(handlerFactory);

        final TestMetricsListener metricsListener = new TestMetricsListener();
        final ApnsClient client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient(metricsListener) : this.buildTlsAuthenticationClient(metricsListener);

        try {
            server.start(PORT).await();

            final SimpleApnsPushNotification pushNotification =
                    new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            final Future<PushNotificationResponse<SimpleApnsPushNotification>> future =
                    client.sendNotification(pushNotification).await();

            assertFalse("Internal server errors should be treated as write failures rather than deliberate rejections.",
                    future.isSuccess());

            assertTrue("Internal server errors should be reported to callers as ApnsServerExceptions.",
                    future.cause() instanceof ApnsServerException);

            client.sendNotification(pushNotification).await();

            assertEquals("Connections should be replaced after receiving an InternalServerError.",
                    1, metricsListener.getConnectionsRemoved().get());
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    @Test
    @Parameters({"true", "false"})
    public void testAcceptedNotificationAndAddedConnectionMetrics(final boolean useTokenAuthentication) throws Exception {
        final MockApnsServer server = this.buildServer(new AcceptAllPushNotificationHandlerFactory());

        final TestMetricsListener metricsListener = new TestMetricsListener();
        final ApnsClient<SimpleApnsPushNotification> client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient(metricsListener) : this.buildTlsAuthenticationClient(metricsListener);

        try {
            server.start(PORT).await();

            final SimpleApnsPushNotification pushNotification =
                    new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            client.sendNotification(pushNotification).await();

            metricsListener.waitForNonZeroAcceptedNotifications();

            assertEquals(1, metricsListener.getSentNotifications().size());
            assertEquals(metricsListener.getSentNotifications(), metricsListener.getAcceptedNotifications());
            assertTrue(metricsListener.getRejectedNotifications().isEmpty());

            assertEquals(1, metricsListener.getConnectionsAdded().get());
            assertEquals(0, metricsListener.getConnectionsRemoved().get());
            assertEquals(0, metricsListener.getFailedConnectionAttempts().get());
        } finally {
            server.shutdown().await();
        }
    }

    @Test
    @Parameters({"true", "false"})
    public void testRejectedNotificationMetrics(final boolean useTokenAuthentication) throws Exception {
        final PushNotificationHandlerFactory handlerFactory = new PushNotificationHandlerFactory() {
            @Override
            public PushNotificationHandler buildHandler(final SSLSession sslSession) {
                return new PushNotificationHandler() {
                    @Override
                    public void handlePushNotification(final Http2Headers headers, final ByteBuf payload) throws RejectedNotificationException {
                        throw new RejectedNotificationException(RejectionReason.BAD_DEVICE_TOKEN, null);
                    }
                };
            }
        };

        final MockApnsServer server = this.buildServer(handlerFactory);

        final TestMetricsListener metricsListener = new TestMetricsListener();
        final ApnsClient client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient(metricsListener) : this.buildTlsAuthenticationClient(metricsListener);

        try {
            server.start(PORT).await();

            final SimpleApnsPushNotification pushNotification =
                    new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            client.sendNotification(pushNotification).await();

            metricsListener.waitForNonZeroRejectedNotifications();

            assertEquals(1, metricsListener.getSentNotifications().size());
            assertEquals(metricsListener.getSentNotifications(), metricsListener.getRejectedNotifications());
            assertTrue(metricsListener.getAcceptedNotifications().isEmpty());
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    @Test
    @Parameters({"true", "false"})
    public void testFailedConnectionAndWriteFailureMetrics(final boolean useTokenAuthentication) throws Exception {
        final TestMetricsListener metricsListener = new TestMetricsListener();

        final ApnsClient client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient(metricsListener) : this.buildTlsAuthenticationClient(metricsListener);

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

        client.sendNotification(pushNotification).await();

        metricsListener.waitForNonZeroFailedConnections();
        metricsListener.waitForNonZeroWriteFailures();

        assertEquals(0, metricsListener.getConnectionsAdded().get());
        assertEquals(0, metricsListener.getConnectionsRemoved().get());
        assertEquals(1, metricsListener.getFailedConnectionAttempts().get());

        assertEquals(1, metricsListener.getWriteFailures().size());
    }

    private ApnsClient buildTlsAuthenticationClient() throws IOException {
        return this.buildTlsAuthenticationClient(null);
    }

    private ApnsClient buildTlsAuthenticationClient(final TestMetricsListener metricsListener) throws IOException {
        try (final InputStream p12InputStream = getClass().getResourceAsStream(MULTI_TOPIC_CLIENT_KEYSTORE_FILENAME)) {
            return new ApnsClientBuilder()
                    .setApnsServer(HOST, PORT)
                    .setClientCredentials(p12InputStream, KEYSTORE_PASSWORD)
                    .setTrustedServerCertificateChain(getClass().getResourceAsStream(CA_CERTIFICATE_FILENAME))
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
                    .setMetricsListener(metricsListener)
                    .build();
        }
    }

    private ApnsClient buildTokenAuthenticationClient() throws SSLException {
        return this.buildTokenAuthenticationClient(null);
    }

    private ApnsClient buildTokenAuthenticationClient(final TestMetricsListener metricsListener) throws SSLException {
        return new ApnsClientBuilder()
                .setApnsServer(HOST, PORT)
                .setTrustedServerCertificateChain(getClass().getResourceAsStream(CA_CERTIFICATE_FILENAME))
                .setSigningKey(this.signingKey)
                .setEventLoopGroup(EVENT_LOOP_GROUP)
                .setMetricsListener(metricsListener)
                .build();
    }

    private MockApnsServer buildServer(final PushNotificationHandlerFactory handlerFactory) throws SSLException {
        return new MockApnsServerBuilder()
                .setServerCredentials(getClass().getResourceAsStream(SERVER_CERTIFICATES_FILENAME), getClass().getResourceAsStream(SERVER_KEY_FILENAME), null)
                .setTrustedClientCertificateChain(getClass().getResourceAsStream(CA_CERTIFICATE_FILENAME))
                .setEventLoopGroup(EVENT_LOOP_GROUP)
                .setHandlerFactory(handlerFactory)
                .build();
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
