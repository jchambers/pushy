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

import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.SslContext;

import javax.net.ssl.SSLSession;

/**
 * <p>A mock APNs server is an HTTP/2 server that can be configured to respond to APNs push notifications with a variety
 * of behaviors. Mock servers are primarily useful for integration tests and benchmarks; users do <strong>not</strong>
 * need to interact with mock servers as part of normal client operation.</p>
 *
 * <p>Callers construct mock APNs servers with the {@link MockApnsServerBuilder} class, and provide a
 * {@link PushNotificationHandlerFactory} at construction time. The factory constructs {@link PushNotificationHandler}
 * instances that control how the server responds to push notifications. Pushy comes with a
 * {@link ValidatingPushNotificationHandlerFactory} that constructs handlers that emulate the behavior of a real APNs
 * server (but do not actually deliver push notifications to destination devices) and an
 * {@link AcceptAllPushNotificationHandlerFactory} that constructs handlers that unconditionally accept push
 * notifications. Additionally, callers may specify a {@link MockApnsServerListener} that will be notified when
 * notifications are accepted or rejected by the server.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.8
 */
public class MockApnsServer extends BaseHttp2Server {

    private final PushNotificationHandlerFactory handlerFactory;
    private final MockApnsServerListener listener;

    private final int maxConcurrentStreams;

    MockApnsServer(final SslContext sslContext, final EventLoopGroup eventLoopGroup,
                   final PushNotificationHandlerFactory handlerFactory, final MockApnsServerListener listener,
                   final int maxConcurrentStreams) {

        super(sslContext, eventLoopGroup);

        this.handlerFactory = handlerFactory;
        this.listener = listener;

        this.maxConcurrentStreams = maxConcurrentStreams;
    }

    @Override
    protected void addHandlersToPipeline(final SSLSession sslSession, final ChannelPipeline pipeline) throws Exception {
        final PushNotificationHandler pushNotificationHandler = this.handlerFactory.buildHandler(sslSession);

        final MockApnsServerHandler serverHandler = new MockApnsServerHandler.MockApnsServerHandlerBuilder()
                .pushNotificationHandler(pushNotificationHandler)
                .initialSettings(Http2Settings.defaultSettings().maxConcurrentStreams(this.maxConcurrentStreams))
                .listener(this.listener)
                .build();

        pipeline.addLast(serverHandler);
    }
}
