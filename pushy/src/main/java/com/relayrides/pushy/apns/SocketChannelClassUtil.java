package com.relayrides.pushy.apns;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioServerSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;

class SocketChannelClassUtil {

    private static final String EPOLL_EVENT_LOOP_GROUP_CLASS = "io.netty.channel.epoll.EpollEventLoopGroup";
    private static final String EPOLL_SOCKET_CHANNEL_CLASS = "io.netty.channel.epoll.EpollSocketChannel";
    private static final String EPOLL_SERVER_SOCKET_CHANNEL_CLASS = "io.netty.channel.epoll.EpollServerSocketChannel";

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
