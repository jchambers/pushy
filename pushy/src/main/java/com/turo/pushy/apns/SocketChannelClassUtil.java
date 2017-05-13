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

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioServerSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

class SocketChannelClassUtil {

    private static final String EPOLL_EVENT_LOOP_GROUP_CLASS = "io.netty.channel.epoll.EpollEventLoopGroup";
    private static final String EPOLL_SOCKET_CHANNEL_CLASS = "io.netty.channel.epoll.EpollSocketChannel";
    private static final String EPOLL_SERVER_SOCKET_CHANNEL_CLASS = "io.netty.channel.epoll.EpollServerSocketChannel";

    private static final String KQUEUE_EVENT_LOOP_GROUP_CLASS = "io.netty.channel.kqueue.KQueueEventLoopGroup";
    private static final String KQUEUE_SOCKET_CHANNEL_CLASS = "io.netty.channel.kqueue.KQueueSocketChannel";
    private static final String KQUEUE_SERVER_SOCKET_CHANNEL_CLASS = "io.netty.channel.kqueue.KQueueServerSocketChannel";

    private static final Logger log = LoggerFactory.getLogger(SocketChannelClassUtil.class);

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
    public static Class<? extends Channel> getSocketChannelClass(final EventLoopGroup eventLoopGroup) {
        Objects.requireNonNull(eventLoopGroup);

        final Class<? extends Channel> socketChannelClass;

        if (eventLoopGroup instanceof NioEventLoopGroup) {
            socketChannelClass = NioSocketChannel.class;
        } else if (eventLoopGroup instanceof OioEventLoopGroup) {
            socketChannelClass = OioSocketChannel.class;
        } else if (EPOLL_EVENT_LOOP_GROUP_CLASS.equals(eventLoopGroup.getClass().getName())) {
            socketChannelClass = loadSocketChannelClass(EPOLL_SOCKET_CHANNEL_CLASS);
        } else if (KQUEUE_EVENT_LOOP_GROUP_CLASS.equals(eventLoopGroup.getClass().getName())) {
            socketChannelClass = loadSocketChannelClass(KQUEUE_SOCKET_CHANNEL_CLASS);
        } else {
            throw new IllegalArgumentException("Could not find socket class for event loop group class: " + eventLoopGroup.getClass().getName());
        }

        return socketChannelClass;
    }

    /**
     * Returns a server socket channel class suitable for specified event loop group.
     *
     * @param eventLoopGroup the event loop group for which to identify an appropriate socket channel class; must not
     * be {@code null}
     *
     * @return a server socket channel class suitable for use with the given event loop group
     *
     * @throws IllegalArgumentException in case of null or unrecognized event loop group
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends ServerChannel> getServerSocketChannelClass(final EventLoopGroup eventLoopGroup) {
        Objects.requireNonNull(eventLoopGroup);

        final Class<? extends ServerChannel> serverSocketChannelClass;

        if (eventLoopGroup instanceof NioEventLoopGroup) {
            serverSocketChannelClass = NioServerSocketChannel.class;
        } else if (eventLoopGroup instanceof OioEventLoopGroup) {
            serverSocketChannelClass = OioServerSocketChannel.class;
        } else if (EPOLL_EVENT_LOOP_GROUP_CLASS.equals(eventLoopGroup.getClass().getName())) {
            serverSocketChannelClass = (Class<? extends ServerChannel>) loadSocketChannelClass(EPOLL_SERVER_SOCKET_CHANNEL_CLASS);
        } else if (KQUEUE_EVENT_LOOP_GROUP_CLASS.equals(eventLoopGroup.getClass().getName())) {
            serverSocketChannelClass = (Class<? extends ServerChannel>) loadSocketChannelClass(KQUEUE_SERVER_SOCKET_CHANNEL_CLASS);
        } else {
            throw new IllegalArgumentException("Could not find server socket class for event loop group class: " + eventLoopGroup.getClass().getName());
        }

        return serverSocketChannelClass;
    }

    private static Class<? extends Channel> loadSocketChannelClass(final String className) {
        try {
            final Class<?> clazz = Class.forName(className);
            log.debug("Loaded socket channel class: {}", clazz);
            return clazz.asSubclass(Channel.class);
        } catch (final ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
