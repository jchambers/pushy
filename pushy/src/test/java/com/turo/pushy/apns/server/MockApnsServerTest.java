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

package com.turo.pushy.apns.server;

import com.turo.pushy.apns.AbstractClientServerTest;
import com.turo.pushy.apns.ApnsClient;
import com.turo.pushy.apns.ApnsPushNotification;
import com.turo.pushy.apns.PushNotificationResponse;
import com.turo.pushy.apns.util.SimpleApnsPushNotification;
import io.netty.buffer.ByteBuf;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import javax.net.ssl.SSLSession;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class MockApnsServerTest extends AbstractClientServerTest {

    private static class TestMockApnsServerListener extends ParsingMockApnsServerListenerAdapter {

        private final AtomicInteger acceptedNotifications = new AtomicInteger(0);
        private final AtomicInteger rejectedNotifications = new AtomicInteger(0);

        private ApnsPushNotification mostRecentPushNotification;
        private RejectionReason mostRecentRejectionReason;
        private Date mostRecentDeviceTokenExpiration;

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
        public void handlePushNotificationRejected(final ApnsPushNotification pushNotification, final RejectionReason rejectionReason, final Date deviceTokenExpirationTimestamp) {
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
    public void testStartAndShutdown() throws Exception {
        final MockApnsServer server = this.buildServer(new AcceptAllPushNotificationHandlerFactory());

        assertTrue(server.start(PORT).await().isSuccess());
        assertTrue(server.shutdown().await().isSuccess());
    }

    @Test
    public void testShutdownBeforeStart() throws Exception {
        final MockApnsServer server = this.buildServer(new AcceptAllPushNotificationHandlerFactory());

        assertTrue(server.shutdown().await().isSuccess());
    }

    @Test
    public void testShutdownWithProvidedEventLoopGroup() throws Exception {
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
    public void testRestartWithProvidedEventLoopGroup() throws Exception {
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
    public void testListenerAcceptedNotification() throws Exception {
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
    public void testListenerRejectedNotification() throws Exception {
        final TestMockApnsServerListener listener = new TestMockApnsServerListener();

        final MockApnsServer server = this.buildServer(new PushNotificationHandlerFactory() {

            @Override
            public PushNotificationHandler buildHandler(final SSLSession sslSession) {
                return new PushNotificationHandler() {

                    @Override
                    public void handlePushNotification(final Http2Headers headers, final ByteBuf payload) throws RejectedNotificationException {
                        throw new RejectedNotificationException(RejectionReason.BAD_DEVICE_TOKEN);
                    }
                };
            }
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
    public void testListenerRejectedNotificationWithExpiration() throws Exception {
        final TestMockApnsServerListener listener = new TestMockApnsServerListener();
        final Date expiration = new Date();

        final MockApnsServer server = this.buildServer(new PushNotificationHandlerFactory() {
            @Override
            public PushNotificationHandler buildHandler(final SSLSession sslSession) {
                return new PushNotificationHandler() {

                    @Override
                    public void handlePushNotification(final Http2Headers headers, final ByteBuf payload) throws RejectedNotificationException {
                        throw new UnregisteredDeviceTokenException(expiration);
                    }
                };
            }
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
    public void testListenerInternalServerError() throws Exception {
        final TestMockApnsServerListener listener = new TestMockApnsServerListener();

        final MockApnsServer server = this.buildServer(new PushNotificationHandlerFactory() {

            @Override
            public PushNotificationHandler buildHandler(final SSLSession sslSession) {
                return new PushNotificationHandler() {

                    @Override
                    public void handlePushNotification(final Http2Headers headers, final ByteBuf payload) {
                        throw new RuntimeException("Everything is terrible.");
                    }
                };
            }
        }, listener);

        final ApnsClient client = this.buildTokenAuthenticationClient();

        try {
            server.start(PORT).await();

            final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            final Future<PushNotificationResponse<SimpleApnsPushNotification>> sendFuture =
                    client.sendNotification(pushNotification).await();

            assertFalse(sendFuture.isSuccess());

            listener.waitForNonZeroRejectedNotifications();

            assertEquals(RejectionReason.INTERNAL_SERVER_ERROR, listener.mostRecentRejectionReason);
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    @Test
    public void testApnsIdForAcceptedNotification() throws Exception {
        final MockApnsServer server = this.buildServer(new AcceptAllPushNotificationHandlerFactory());
        final ApnsClient client = this.buildTokenAuthenticationClient();

        try {
            server.start(PORT).await();

            final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            final PushNotificationResponse<SimpleApnsPushNotification> response =
                    client.sendNotification(pushNotification).get();

            assertTrue(response.isAccepted());
            assertNotNull(response.getApnsId());
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }

    @Test
    public void testApnsIdForRejectedNotification() throws Exception {
        final MockApnsServer server = this.buildServer(new PushNotificationHandlerFactory() {

            @Override
            public PushNotificationHandler buildHandler(final SSLSession sslSession) {
                return new PushNotificationHandler() {

                    @Override
                    public void handlePushNotification(final Http2Headers headers, final ByteBuf payload) throws RejectedNotificationException {
                        throw new RejectedNotificationException(RejectionReason.MISSING_TOPIC);
                    }
                };
            }
        });

        final ApnsClient client = this.buildTokenAuthenticationClient();

        try {
            server.start(PORT).await();

            final SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(DEVICE_TOKEN, TOPIC, PAYLOAD);

            final PushNotificationResponse<SimpleApnsPushNotification> response =
                    client.sendNotification(pushNotification).get();

            assertFalse(response.isAccepted());
            assertNotNull(response.getApnsId());
        } finally {
            client.close().await();
            server.shutdown().await();
        }
    }
}
