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

import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.*;
import io.netty.channel.kqueue.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringDatagramChannel;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringSocketChannel;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ClientChannelClassUtilTest {

    @Nested
    class NioTransport {

        private IoEventLoopGroup ioEventLoopGroup;
        private NioEventLoopGroup legacyEventLoopGroup;

        @BeforeEach
        void setUp() {
            ioEventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
            legacyEventLoopGroup = new NioEventLoopGroup();
        }

        @Test
        void getSocketChannelClass() {
            assertEquals(NioSocketChannel.class, ClientChannelClassUtil.getSocketChannelClass(ioEventLoopGroup));
            assertEquals(NioSocketChannel.class, ClientChannelClassUtil.getSocketChannelClass(legacyEventLoopGroup));
        }

        @Test
        void getDatagramClass() {
            assertEquals(NioDatagramChannel.class, ClientChannelClassUtil.getDatagramChannelClass(ioEventLoopGroup));
            assertEquals(NioDatagramChannel.class, ClientChannelClassUtil.getDatagramChannelClass(legacyEventLoopGroup));
        }

        @AfterEach
        void tearDown() {
            ioEventLoopGroup.shutdownGracefully();
            legacyEventLoopGroup.shutdownGracefully();
        }
    }

    @Nested
    class EpollTransport {

        private IoEventLoopGroup ioEventLoopGroup;
        private EpollEventLoopGroup legacyEventLoopGroup;

        @BeforeEach
        void setUp() {
            final String unavailabilityMessage =
                Epoll.unavailabilityCause() != null ? Epoll.unavailabilityCause().getMessage() : null;

            assumeTrue(Epoll.isAvailable(), "Epoll not available: " + unavailabilityMessage);

            ioEventLoopGroup = new MultiThreadIoEventLoopGroup(EpollIoHandler.newFactory());
            legacyEventLoopGroup = new EpollEventLoopGroup();
        }

        @Test
        void getSocketChannelClass() {
            assertEquals(EpollSocketChannel.class, ClientChannelClassUtil.getSocketChannelClass(ioEventLoopGroup));
            assertEquals(EpollSocketChannel.class, ClientChannelClassUtil.getSocketChannelClass(legacyEventLoopGroup));
        }

        @Test
        void getDatagramClass() {
            assertEquals(EpollDatagramChannel.class, ClientChannelClassUtil.getDatagramChannelClass(ioEventLoopGroup));
            assertEquals(EpollDatagramChannel.class, ClientChannelClassUtil.getDatagramChannelClass(legacyEventLoopGroup));
        }

        @AfterEach
        void tearDown() {
            if (ioEventLoopGroup != null) {
                ioEventLoopGroup.shutdownGracefully();
            }

            if (legacyEventLoopGroup != null) {
                legacyEventLoopGroup.shutdownGracefully();
            }
        }
    }

    @Nested
    class IoUringTransport {

        private IoEventLoopGroup ioEventLoopGroup;

        @BeforeEach
        void setUp() {
            final String unavailabilityMessage =
                IoUring.unavailabilityCause() != null ? IoUring.unavailabilityCause().getMessage() : null;

            assumeTrue(IoUring.isAvailable(), "io_uring not available: " + unavailabilityMessage);

            ioEventLoopGroup = new MultiThreadIoEventLoopGroup(IoUringIoHandler.newFactory());
        }

        @Test
        void getSocketChannelClass() {
            assertEquals(IoUringSocketChannel.class, ClientChannelClassUtil.getSocketChannelClass(ioEventLoopGroup));
        }

        @Test
        void getDatagramClass() {
            assertEquals(IoUringDatagramChannel.class, ClientChannelClassUtil.getDatagramChannelClass(ioEventLoopGroup));
        }

        @AfterEach
        void tearDown() {
            if (ioEventLoopGroup != null) {
                ioEventLoopGroup.shutdownGracefully();
            }
        }
    }

    @Nested
    class KQueueTransport {

        private IoEventLoopGroup ioEventLoopGroup;
        private KQueueEventLoopGroup legacyEventLoopGroup;

        @BeforeEach
        void setUp() {
            final String unavailabilityMessage =
                KQueue.unavailabilityCause() != null ? KQueue.unavailabilityCause().getMessage() : null;

            assumeTrue(KQueue.isAvailable(), "KQueue not available: " + unavailabilityMessage);

            ioEventLoopGroup = new MultiThreadIoEventLoopGroup(KQueueIoHandler.newFactory());
            legacyEventLoopGroup = new KQueueEventLoopGroup();
        }

        @Test
        void getSocketChannelClass() {
            assertEquals(KQueueSocketChannel.class, ClientChannelClassUtil.getSocketChannelClass(ioEventLoopGroup));
            assertEquals(KQueueSocketChannel.class, ClientChannelClassUtil.getSocketChannelClass(legacyEventLoopGroup));
        }

        @Test
        void getDatagramClass() {
            assertEquals(KQueueDatagramChannel.class, ClientChannelClassUtil.getDatagramChannelClass(ioEventLoopGroup));
            assertEquals(KQueueDatagramChannel.class, ClientChannelClassUtil.getDatagramChannelClass(legacyEventLoopGroup));
        }

        @AfterEach
        void tearDown() {
            if (ioEventLoopGroup != null) {
                ioEventLoopGroup.shutdownGracefully();
            }

            if (legacyEventLoopGroup != null) {
                legacyEventLoopGroup.shutdownGracefully();
            }
        }
    }
}
