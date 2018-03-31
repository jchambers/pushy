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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class ClientChannelClassUtil {

    private static final Map<String, String> SOCKET_CHANNEL_CLASSES = new HashMap<>();
    private static final Map<String, String> DATAGRAM_CHANNEL_CLASSES = new HashMap<>();

    static {
        SOCKET_CHANNEL_CLASSES.put("io.netty.channel.nio.NioEventLoopGroup", "io.netty.channel.socket.nio.NioSocketChannel");
        SOCKET_CHANNEL_CLASSES.put("io.netty.channel.oio.OioEventLoopGroup", "io.netty.channel.socket.oio.OioSocketChannel");
        SOCKET_CHANNEL_CLASSES.put("io.netty.channel.epoll.EpollEventLoopGroup", "io.netty.channel.epoll.EpollSocketChannel");
        SOCKET_CHANNEL_CLASSES.put("io.netty.channel.kqueue.KQueueEventLoopGroup", "io.netty.channel.kqueue.KQueueSocketChannel");

        DATAGRAM_CHANNEL_CLASSES.put("io.netty.channel.nio.NioEventLoopGroup", "io.netty.channel.socket.nio.NioDatagramChannel");
        DATAGRAM_CHANNEL_CLASSES.put("io.netty.channel.oio.OioEventLoopGroup", "io.netty.channel.socket.oio.OioDatagramChannel");
        DATAGRAM_CHANNEL_CLASSES.put("io.netty.channel.epoll.EpollEventLoopGroup", "io.netty.channel.epoll.EpollDatagramChannel");
        DATAGRAM_CHANNEL_CLASSES.put("io.netty.channel.kqueue.KQueueEventLoopGroup", "io.netty.channel.kqueue.KQueueDatagramChannel");
    }

    /**
     * Returns a socket channel class suitable for specified event loop group.
     *
     * @param eventLoopGroup the event loop group for which to identify an appropriate socket channel class; must not
     * be {@code null}
     *
     * @return a socket channel class suitable for use with the given event loop group
     *
     * @throws IllegalArgumentException in case of null or unrecognized event loop group
     */
    static Class<? extends SocketChannel> getSocketChannelClass(final EventLoopGroup eventLoopGroup) {
        Objects.requireNonNull(eventLoopGroup);

        final String socketChannelClassName = SOCKET_CHANNEL_CLASSES.get(eventLoopGroup.getClass().getName());

        if (socketChannelClassName == null) {
            throw new IllegalArgumentException("No socket channel class found for event loop group type: " + eventLoopGroup.getClass().getName());
        }

        try {
            return Class.forName(socketChannelClassName).asSubclass(SocketChannel.class);
        } catch (final ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns a datagram channel class suitable for specified event loop group.
     *
     * @param eventLoopGroup the event loop group for which to identify an appropriate datagram channel class; must not
     * be {@code null}
     *
     * @return a datagram channel class suitable for use with the given event loop group
     *
     * @throws IllegalArgumentException in case of null or unrecognized event loop group
     */
    static Class<? extends DatagramChannel> getDatagramChannelClass(final EventLoopGroup eventLoopGroup) {
        Objects.requireNonNull(eventLoopGroup);

        final String datagramChannelClassName = DATAGRAM_CHANNEL_CLASSES.get(eventLoopGroup.getClass().getName());

        if (datagramChannelClassName == null) {
            throw new IllegalArgumentException("No datagram channel class found for event loop group type: " + eventLoopGroup.getClass().getName());
        }

        try {
            return Class.forName(datagramChannelClassName).asSubclass(DatagramChannel.class);
        } catch (final ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
