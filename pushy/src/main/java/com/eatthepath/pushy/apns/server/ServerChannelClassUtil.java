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

import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.IoHandler;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.ServerSocketChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class ServerChannelClassUtil {

    private static final Map<String, String> SERVER_SOCKET_CHANNEL_CLASSES = new HashMap<>();

    static {
        SERVER_SOCKET_CHANNEL_CLASSES.put("io.netty.channel.nio.NioIoHandler", "io.netty.channel.socket.nio.NioServerSocketChannel");
        SERVER_SOCKET_CHANNEL_CLASSES.put("io.netty.channel.uring.IoUringIoHandler", "io.netty.channel.uring.IoUringServerSocketChannel");
        SERVER_SOCKET_CHANNEL_CLASSES.put("io.netty.channel.epoll.EpollIoHandler", "io.netty.channel.epoll.EpollServerSocketChannel");
        SERVER_SOCKET_CHANNEL_CLASSES.put("io.netty.channel.kqueue.KQueueIoHandler", "io.netty.channel.kqueue.KQueueServerSocketChannel");
    }

    /**
     * Returns a server socket channel class suitable for specified event loop group.
     *
     * @param ioEventLoopGroup the event loop group for which to identify an appropriate socket channel class; must not
     * be {@code null}
     *
     * @return a server socket channel class suitable for use with the given event loop group
     *
     * @throws IllegalArgumentException if no suitable server socket channel class could be found for the given event
     * loop group
     * @throws NullPointerException if the given {@code ioEventLoopGroup} was {@code null}
     */
    static Class<? extends ServerChannel> getServerSocketChannelClass(final IoEventLoopGroup ioEventLoopGroup) {
        Objects.requireNonNull(ioEventLoopGroup);

        for (final Map.Entry<String, String> entry : SERVER_SOCKET_CHANNEL_CLASSES.entrySet()) {
            try {
                final Class<? extends IoHandler> ioHandlerClass =
                    Class.forName(entry.getKey()).asSubclass(IoHandler.class);

                if (ioEventLoopGroup.isIoType(ioHandlerClass)) {
                    return Class.forName(entry.getValue()).asSubclass(ServerSocketChannel.class);
                }
            } catch (final ClassNotFoundException e) {
                continue;
            }
        }

        throw new IllegalArgumentException("No suitable channel class found for event loop group");
    }
}
