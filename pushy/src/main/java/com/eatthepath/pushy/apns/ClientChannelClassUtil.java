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

import io.netty.channel.Channel;
import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.IoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class ClientChannelClassUtil {

    private static final Map<String, String> SOCKET_CHANNEL_CLASSES = new HashMap<>();
    private static final Map<String, String> DATAGRAM_CHANNEL_CLASSES = new HashMap<>();

    static {
        SOCKET_CHANNEL_CLASSES.put("io.netty.channel.nio.NioIoHandler", "io.netty.channel.socket.nio.NioSocketChannel");
        SOCKET_CHANNEL_CLASSES.put("io.netty.channel.uring.IoUringIoHandler", "io.netty.channel.uring.IoUringSocketChannel");
        SOCKET_CHANNEL_CLASSES.put("io.netty.channel.epoll.EpollIoHandler", "io.netty.channel.epoll.EpollSocketChannel");
        SOCKET_CHANNEL_CLASSES.put("io.netty.channel.kqueue.KQueueIoHandler", "io.netty.channel.kqueue.KQueueSocketChannel");

        DATAGRAM_CHANNEL_CLASSES.put("io.netty.channel.nio.NioIoHandler", "io.netty.channel.socket.nio.NioDatagramChannel");
        DATAGRAM_CHANNEL_CLASSES.put("io.netty.channel.uring.IoUringIoHandler", "io.netty.channel.uring.IoUringDatagramChannel");
        DATAGRAM_CHANNEL_CLASSES.put("io.netty.channel.epoll.EpollIoHandler", "io.netty.channel.epoll.EpollDatagramChannel");
        DATAGRAM_CHANNEL_CLASSES.put("io.netty.channel.kqueue.KQueueIoHandler", "io.netty.channel.kqueue.KQueueDatagramChannel");
    }

    /**
     * Returns a socket channel class suitable for specified event loop group.
     *
     * @param ioEventLoopGroup the event loop group for which to identify an appropriate socket channel class; must not
     * be {@code null}
     *
     * @return a socket channel class suitable for use with the given event loop group
     *
     * @throws IllegalArgumentException if no suitable socket channel class could be found for the given event loop
     * group
     * @throws NullPointerException if the given {@code ioEventLoopGroup} was {@code null}
     */
    static Class<? extends SocketChannel> getSocketChannelClass(final IoEventLoopGroup ioEventLoopGroup) {
        return getChannelClass(Objects.requireNonNull(ioEventLoopGroup), SOCKET_CHANNEL_CLASSES, SocketChannel.class);
    }

    /**
     * Returns a datagram channel class suitable for specified event loop group.
     *
     * @param ioEventLoopGroup the event loop group for which to identify an appropriate datagram channel class; must
     * not be {@code null}
     *
     * @return a datagram channel class suitable for use with the given event loop group
     *
     * @throws IllegalArgumentException if no suitable datagram channel class could be found for the given event loop
     * group
     * @throws NullPointerException if the given {@code ioEventLoopGroup} was {@code null}
     */
    static Class<? extends DatagramChannel> getDatagramChannelClass(final IoEventLoopGroup ioEventLoopGroup) {
        return getChannelClass(Objects.requireNonNull(ioEventLoopGroup), DATAGRAM_CHANNEL_CLASSES, DatagramChannel.class);
    }

    private static <C extends Channel> Class<? extends C> getChannelClass(final IoEventLoopGroup ioEventLoopGroup,
                                                                          final Map<String, String> channelClassesByIoHandlerClass,
                                                                          final Class<C> channelType) {

        for (final Map.Entry<String, String> entry : channelClassesByIoHandlerClass.entrySet()) {
          try {
            final Class<? extends IoHandler> ioHandlerClass =
                Class.forName(entry.getKey()).asSubclass(IoHandler.class);

              if (ioEventLoopGroup.isIoType(ioHandlerClass)) {
                  return Class.forName(entry.getValue()).asSubclass(channelType);
              }
          } catch (final ClassNotFoundException e) {
            continue;
          }
        }

        throw new IllegalArgumentException("No suitable channel class found for event loop group");
    }
}
