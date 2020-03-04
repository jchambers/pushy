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

package com.eatthepath.pushy.apns.server;

import com.eatthepath.pushy.apns.*;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class MockApnsServerTest extends AbstractClientServerTest {

    private static class TestMockApnsServerListener extends ParsingMockApnsServerListenerAdapter {

        private final AtomicInteger acceptedNotifications = new AtomicInteger(0);
        private final AtomicInteger rejectedNotifications = new AtomicInteger(0);

        private ApnsPushNotification mostRecentPushNotification;
        private RejectionReason mostRecentRejectionReason;
        private Instant mostRecentDeviceTokenExpiration;

        @Override
        public void handlePushNotificationAccepted(final ApnsPushNotification pushNotification) {
            this.mostRecentPushNotification = pushNotification;
            this.mostRecentRejectionReason = null;
            this.mostRecentDeviceTokenExpiration = null;

            this.acceptedNotifications.incrementAndGet();

            synchronized (this.acceptedNotifications) {
                this.acceptedNotifications.notifyAll();
            }
        }

        public void waitForNonZeroAcceptedNotifications() throws InterruptedException {
            synchronized (this.acceptedNotifications) {
                while (this.acceptedNotifications.get() == 0) {
                    this.acceptedNotifications.wait();
                }
            }
        }

        @Override
        public void handlePushNotificationRejected(final ApnsPushNotification pushNotification, final RejectionReason rejectionReason, final Instant deviceTokenExpirationTimestamp) {
            this.mostRecentPushNotification = pushNotification;
            this.mostRecentRejectionReason = rejectionReason;
            this.mostRecentDeviceTokenExpiration = deviceTokenExpirationTimestamp;

            this.rejectedNotifications.incrementAndGet();

            synchronized (this.rejectedNotifications) {
                this.rejectedNotifications.notifyAll();
            }
        }

        public void waitForNonZeroRejectedNotifications() throws InterruptedException {
            synchronized (this.rejectedNotifications) {
                while (this.rejectedNotifications.get() == 0) {
                    this.rejectedNotifications.wait();
                }
            }
        }
    }

    @Test
    void testStartAndShutdown() throws Exception {
        final MockApnsServer server = this.buildServer(new AcceptAllPushNotificationHandlerFactory());

        assertTrue(server.start(PORT).await().isSuccess());
        assertTrue(server.shutdown().await().isSuccess());
    }

    @Test
    void testShutdownBeforeStart() throws Exception {
        final MockApnsServer server = this.buildServer(new AcceptAllPushNotificationHandlerFactory());

        assertTrue(server.shutdown().await().isSuccess());
    }

    @Test
    void testShutdownWithProvidedEventLoopGroup() throws Exception {
        final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);

        try {

            final MockApnsServer providedGroupServer = new MockApnsServerBuilder()
                    .setServerCredentials(getClass().getResourceAsStream(SERVER_CERTIFICATES_FILENAME), getClass().getResourceAsStream(SERVER_KEY_FILENAME), null)
                    .setHandlerFactory(new AcceptAllPushNotificationHandlerFactory())
                    .setEventLoopGroup(eventLoopGroup)
                    .build();

            assertTrue(providedGroupServer.start(PORT).await().isSuccess());
            assertTrue(providedGroupServer.shutdown().await().isSuccess());

            assertFalse(eventLoopGroup.isShutdown());
        } finally {
            eventLoopGroup.shutdownGracefully().await();
        }
    }

    @Test
    void testRestartWithProvidedEventLoopGroup() throws Exception {
        int javaVersion = 0;

        try {
            javaVersion = Integer.parseInt(System.getProperty("java.specification.version"));
        } catch (final NumberFormatException ignored) {
        }

        // TODO Remove this assumption when https://github.com/netty/netty/issues/8697 gets resolved
        assumeTrue(javaVersion < 11);

        final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);

        try {
            final MockApnsServer providedGroupServer = new MockApnsServerBuilder()
                    .setServerCredentials(getClass().getResourceAsStream(SERVER_CERTIFICATES_FILENAME), getClass().getResourceAsStream(SERVER_KEY_FILENAME), null)
                    .setHandlerFactory(new AcceptAllPushNotificationHandlerFactory())
                    .setEventLoopGroup(eventLoopGroup)
                    .build();

            assertTrue(providedGroupServer.start(PORT).await().isSuccess());
            assertTrue(providedGroupServer.shutdown().await().isSuccess());

            assertTrue(providedGroupServer.start(PORT).await().isSuccess());
            assertTrue(providedGroupServer.shutdown().await().isSuccess());
        } finally {
            eventLoopGroup.shutdownGracefully().await();
        }
    }

    @Test
    void testListenerAcceptedNotification() throws Exception {
        final TestMockApnsServerListener listener = new TestMockApnsServerListener();

        final MockApnsServer server = this.buildServer(new AcceptAllPushNotificationHandlerFactory(), listener);
        final ApnsClient client = this.buildTokenAuthenticationClient();

        try {
            server.start(PORT).await();

            final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            final PushNotificationResponse<SimpleApnsPushNotification> response =
                    client.sendNotification(pushNotification).get();

            assertTrue(response.isAccepted());

            listener.waitForNonZeroAcceptedNotifications();

            assertEquals(pushNotification.getToken(), listener.mostRecentPushNotification.getToken());
            assertEquals(pushNotification.getTopic(), listener.mostRecentPushNotification.getTopic());
            assertEquals(pushNotification.getPayload(), listener.mostRecentPushNotification.getPayload());
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    @Test
    void testListenerRejectedNotification() throws Exception {
        final TestMockApnsServerListener listener = new TestMockApnsServerListener();

        final MockApnsServer server = this.buildServer(sslSession -> (headers, payload) -> {
            throw new RejectedNotificationException(RejectionReason.BAD_DEVICE_TOKEN);
        }, listener);

        final ApnsClient client = this.buildTokenAuthenticationClient();

        try {
            server.start(PORT).await();

            final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            final PushNotificationResponse<SimpleApnsPushNotification> response =
                    client.sendNotification(pushNotification).get();

            assertFalse(response.isAccepted());

            listener.waitForNonZeroRejectedNotifications();

            assertEquals(RejectionReason.BAD_DEVICE_TOKEN, listener.mostRecentRejectionReason);
            assertNull(listener.mostRecentDeviceTokenExpiration);
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    @Test
    void testListenerRejectedNotificationWithExpiration() throws Exception {
        final TestMockApnsServerListener listener = new TestMockApnsServerListener();
        final Instant expiration = Instant.now();

        final MockApnsServer server = this.buildServer(sslSession -> (headers, payload) -> {
            throw new UnregisteredDeviceTokenException(expiration);
        }, listener);

        final ApnsClient client = this.buildTokenAuthenticationClient();

        try {
            server.start(PORT).await();

            final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            final PushNotificationResponse<SimpleApnsPushNotification> response =
                    client.sendNotification(pushNotification).get();

            assertFalse(response.isAccepted());

            listener.waitForNonZeroRejectedNotifications();

            assertEquals(RejectionReason.UNREGISTERED, listener.mostRecentRejectionReason);
            assertEquals(expiration, listener.mostRecentDeviceTokenExpiration);
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    @Test
    void testListenerInternalServerError() throws Exception {
        final TestMockApnsServerListener listener = new TestMockApnsServerListener();

        final MockApnsServer server = this.buildServer(sslSession -> (headers, payload) -> {
            throw new RuntimeException("Everything is terrible.");
        }, listener);

        final ApnsClient client = this.buildTokenAuthenticationClient();

        try {
            server.start(PORT).await();

            final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            final PushNotificationResponse<SimpleApnsPushNotification> response =
                    client.sendNotification(pushNotification).get();

            assertNotNull(response);

            listener.waitForNonZeroRejectedNotifications();

            assertEquals(RejectionReason.INTERNAL_SERVER_ERROR, listener.mostRecentRejectionReason);
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    @Test
    void testApnsIdForAcceptedNotification() throws Exception {
        final MockApnsServer server = this.buildServer(new AcceptAllPushNotificationHandlerFactory());
        final ApnsClient client = this.buildTokenAuthenticationClient();

        try {
            server.start(PORT).await();

            {
                final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

                final PushNotificationResponse<SimpleApnsPushNotification> response =
                        client.sendNotification(pushNotification).get();

                assertTrue(response.isAccepted());
                assertNotNull(response.getApnsId());
            }

            {
                final UUID apnsId = UUID.randomUUID();

                final SimpleApnsPushNotification pushNotificationWithApnsId =
                        new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD, null, DeliveryPriority.IMMEDIATE, PushType.ALERT, null, apnsId);

                final PushNotificationResponse<SimpleApnsPushNotification> response =
                        client.sendNotification(pushNotificationWithApnsId).get();

                assertTrue(response.isAccepted());
                assertNotNull(response.getApnsId());
                assertEquals(pushNotificationWithApnsId.getApnsId(), response.getApnsId());
            }
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    @Test
    void testApnsIdForRejectedNotification() throws Exception {
        final MockApnsServer server = this.buildServer(sslSession -> (headers, payload) -> {
            throw new RejectedNotificationException(RejectionReason.MISSING_TOPIC);
        });

        final ApnsClient client = this.buildTokenAuthenticationClient();

        try {
            server.start(PORT).await();

            {
                final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

                final PushNotificationResponse<SimpleApnsPushNotification> response =
                        client.sendNotification(pushNotification).get();

                assertFalse(response.isAccepted());
                assertNotNull(response.getApnsId());
            }

            {
                final UUID apnsId = UUID.randomUUID();

                final SimpleApnsPushNotification pushNotificationWithApnsId =
                        new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD, null, DeliveryPriority.IMMEDIATE, PushType.ALERT, null, apnsId);

                final PushNotificationResponse<SimpleApnsPushNotification> response =
                        client.sendNotification(pushNotificationWithApnsId).get();

                assertFalse(response.isAccepted());
                assertNotNull(response.getApnsId());
                assertEquals(pushNotificationWithApnsId.getApnsId(), response.getApnsId());
            }
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }
}
