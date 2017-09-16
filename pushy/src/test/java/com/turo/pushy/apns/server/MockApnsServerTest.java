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

import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MockApnsServerTest {

    private static final String SERVER_CERTIFICATES_FILENAME = "/server-certs.pem";
    private static final String SERVER_KEY_FILENAME = "/server-key.pem";

    private static final int PORT = 8443;

    private MockApnsServer server;

    @Before
    public void setUp() throws Exception {
        this.server = new MockApnsServerBuilder()
                .setServerCredentials(MockApnsServerTest.class.getResourceAsStream(SERVER_CERTIFICATES_FILENAME), MockApnsServerTest.class.getResourceAsStream(SERVER_KEY_FILENAME), null)
                .setHandlerFactory(new AcceptAllPushNotificationHandlerFactory())
                .build();
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

        try {

            final MockApnsServer providedGroupServer = new MockApnsServerBuilder()
                    .setServerCredentials(MockApnsServerTest.class.getResourceAsStream(SERVER_CERTIFICATES_FILENAME), MockApnsServerTest.class.getResourceAsStream(SERVER_KEY_FILENAME), null)
                    .setHandlerFactory(new AcceptAllPushNotificationHandlerFactory())
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

        try {

            final MockApnsServer providedGroupServer = new MockApnsServerBuilder()
                    .setServerCredentials(MockApnsServerTest.class.getResourceAsStream(SERVER_CERTIFICATES_FILENAME), MockApnsServerTest.class.getResourceAsStream(SERVER_KEY_FILENAME), null)
                    .setHandlerFactory(new AcceptAllPushNotificationHandlerFactory())
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
