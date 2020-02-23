/*
 * Copyright (c) 2020 Jon Chambers
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

package com.eatthepath.pushy.apns;

import com.eatthepath.pushy.apns.server.*;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationResponseListener;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(JUnitParamsRunner.class)
public class ApnsClientTest extends AbstractClientServerTest {

    private static class TestClientMetricsListener implements ApnsClientMetricsListener {

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

    private static class TestParsingServerHandler extends ParsingMockApnsServerListenerAdapter {

        private final List<ApnsPushNotification> acceptedNotifications = new ArrayList<>();
        private final List<ApnsPushNotification> rejectedNotifications = new ArrayList<>();

        @Override
        public void handlePushNotificationAccepted(final ApnsPushNotification pushNotification) {
            synchronized (this.acceptedNotifications) {
                this.acceptedNotifications.add(pushNotification);
                this.acceptedNotifications.notifyAll();
            }
        }

        @Override
        public void handlePushNotificationRejected(final ApnsPushNotification pushNotification, final RejectionReason rejectionReason, final Date deviceTokenExpirationTimestamp) {
            synchronized (this.rejectedNotifications) {
                this.rejectedNotifications.add(pushNotification);
                this.rejectedNotifications.notifyAll();
            }
        }

        void waitForNonZeroAcceptedNotifications() throws InterruptedException {
            synchronized (this.acceptedNotifications) {
                while (this.acceptedNotifications.isEmpty()) {
                    this.acceptedNotifications.wait();
                }
            }
        }
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
                    .setEventLoopGroup(CLIENT_EVENT_LOOP_GROUP)
                    .build();
        } else {
            try (final InputStream p12InputStream = getClass().getResourceAsStream(MULTI_TOPIC_CLIENT_KEYSTORE_FILENAME)) {
                cautiousClient = new ApnsClientBuilder()
                        .setApnsServer(HOST, PORT)
                        .setClientCredentials(p12InputStream, KEYSTORE_PASSWORD)
                        .setEventLoopGroup(CLIENT_EVENT_LOOP_GROUP)
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

            boolean hasSSLHandshakeException = false;
            {
                Throwable cause = sendFuture.cause();

                while (!hasSSLHandshakeException && cause != null) {
                    hasSSLHandshakeException = (cause instanceof SSLHandshakeException);
                    cause = cause.getCause();
                }
            }

            assertTrue("Clients should refuse to connect to untrusted servers due to an SSL handshake failure.",
                    hasSSLHandshakeException);
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
        final ApnsClient client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient() : this.buildTlsAuthenticationClient();

        try {
            server.start(PORT).await();

            final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            final PushNotificationResponse<SimpleApnsPushNotification> response =
                    client.sendNotification(pushNotification).get();

            assertTrue("Clients must send notifications that conform to the APNs protocol specification.",
                    response.isAccepted());

            assertNotNull(response.getApnsId());
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

        final TestClientMetricsListener metricsListener = new TestClientMetricsListener();
        final ApnsClient client = this.buildTokenAuthenticationClient(metricsListener);

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
                    public void operationComplete(final Future<PushNotificationResponse<SimpleApnsPushNotification>> future) {
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
                    public void operationComplete(final Future<PushNotificationResponse<SimpleApnsPushNotification>> future) {
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
                        throw new UnregisteredDeviceTokenException(expiration);
                    }
                };
            }
        };

        final SimpleApnsPushNotification pushNotification =
                new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

        final MockApnsServer server = this.buildServer(handlerFactory);
        final ApnsClient client = useTokenAuthentication ?
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
    public void testAcceptedNotificationAndAddedConnectionMetrics(final boolean useTokenAuthentication) throws Exception {
        final MockApnsServer server = this.buildServer(new AcceptAllPushNotificationHandlerFactory());

        final TestClientMetricsListener metricsListener = new TestClientMetricsListener();
        final ApnsClient client = useTokenAuthentication ?
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
            client.close().await();
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
                        throw new RejectedNotificationException(RejectionReason.BAD_DEVICE_TOKEN);
                    }
                };
            }
        };

        final MockApnsServer server = this.buildServer(handlerFactory);

        final TestClientMetricsListener metricsListener = new TestClientMetricsListener();
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
        final TestClientMetricsListener metricsListener = new TestClientMetricsListener();

        final ApnsClient client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient(metricsListener) : this.buildTlsAuthenticationClient(metricsListener);

        try {
            final SimpleApnsPushNotification pushNotification =
                    new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            client.sendNotification(pushNotification).await();

            metricsListener.waitForNonZeroFailedConnections();
            metricsListener.waitForNonZeroWriteFailures();

            assertEquals(0, metricsListener.getConnectionsAdded().get());
            assertEquals(0, metricsListener.getConnectionsRemoved().get());
            assertEquals(1, metricsListener.getFailedConnectionAttempts().get());

            assertEquals(1, metricsListener.getWriteFailures().size());
        } finally {
            client.close().await();
        }
    }

    @Test
    @Parameters({"true", "false"})
    public void testRepeatedlySendNotificationAfterConnectionFailure(final boolean useTokenAuthentication) throws Exception {
        final ApnsClient client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient() : this.buildTlsAuthenticationClient();

        try {
            final SimpleApnsPushNotification pushNotification =
                    new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            for (int i = 0; i < 3; i++) {
                // We should see delays of roughly 0, 1, and 2 seconds; 4 seconds per notification is excessive, but
                // better to play it safe with a timed assertion.
                final Future<PushNotificationResponse<SimpleApnsPushNotification>> sendFuture =
                        client.sendNotification(pushNotification);

                assertTrue(sendFuture.await(4, TimeUnit.SECONDS));
                assertFalse(sendFuture.isSuccess());
            }
        } finally {
            client.close().await();
        }
    }

    @Test
    @Parameters({"true", "false"})
    public void testRepeatedlySendNotificationAfterConnectionFailureWithListeners(final boolean useTokenAuthentication) throws Exception {
        final ApnsClient client = useTokenAuthentication ?
                this.buildTokenAuthenticationClient() : this.buildTlsAuthenticationClient();

        try {
            final SimpleApnsPushNotification pushNotification =
                    new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            final int notificationCount = 3;

            final CountDownLatch countDownLatch = new CountDownLatch(notificationCount);

            for (int i = 0; i < notificationCount; i++) {
                client.sendNotification(pushNotification).addListener(new PushNotificationResponseListener<SimpleApnsPushNotification>() {

                    @Override
                    public void operationComplete(final PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> simpleApnsPushNotificationPushNotificationResponsePushNotificationFuture) {
                        countDownLatch.countDown();
                    }
                });
            }

            // We should see delays of roughly 0, 1, and 2 seconds (for a total of 3 seconds); waiting 6 seconds in
            // total is overkill, but it's best to leave significant margin on timed assertions.
            assertTrue(countDownLatch.await(6, TimeUnit.SECONDS));
        } finally {
            client.close().await();
        }
    }

    @Test
    @Parameters(method = "getParametersForTestSendNotificationWithPushTypeHeader")
    public void testSendNotificationWithPushTypeHeader(final PushType pushType) throws Exception {
        final ValidatingPushNotificationHandlerFactory handlerFactory = new ValidatingPushNotificationHandlerFactory(
                DEVICE_TOKENS_BY_TOPIC, EXPIRATION_TIMESTAMPS_BY_DEVICE_TOKEN, this.verificationKeysByKeyId,
                this.topicsByVerificationKey);

        final TestParsingServerHandler parsingServerHandler = new TestParsingServerHandler();

        final MockApnsServer server = this.buildServer(handlerFactory, parsingServerHandler);

        final ApnsClient client = this.buildTokenAuthenticationClient();

        try {
            server.start(PORT).await();

            final SimpleApnsPushNotification pushNotification =
                    new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD, null, DeliveryPriority.IMMEDIATE, pushType);

            final PushNotificationResponse<SimpleApnsPushNotification> response =
                    client.sendNotification(pushNotification).get();

            assertTrue("Clients must send notifications that conform to the APNs protocol specification.",
                    response.isAccepted());

            parsingServerHandler.waitForNonZeroAcceptedNotifications();

            assertEquals(pushType, parsingServerHandler.acceptedNotifications.get(0).getPushType());
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    @SuppressWarnings("unused")
    private Object getParametersForTestSendNotificationWithPushTypeHeader() {
        return new Object[] {
                null,
                PushType.BACKGROUND,
                PushType.ALERT,
                PushType.VOIP,
                PushType.COMPLICATION,
                PushType.FILEPROVIDER,
                PushType.MDM
        };
    }
}
