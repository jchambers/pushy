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

import io.netty.channel.ChannelPipeline;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * An APNs channel factory creates new channels connected to an APNs server. Channels constructed by this factory are
 * intended for use in an {@link ApnsChannelPool}.
 */
class ApnsNotificationChannelFactory extends AbstractApnsChannelFactory {

    private final ApnsClientConfiguration clientConfiguration;

    ApnsNotificationChannelFactory(final ApnsClientConfiguration clientConfiguration,
                                   final ApnsClientResources clientResources) {

        super(clientConfiguration.getApnsServerAddress(),
            clientConfiguration.getSslContext(),
            clientConfiguration.getProxyHandlerFactory().orElse(null),
            clientConfiguration.isHostnameVerificationEnabled(),
            clientConfiguration.getConnectionTimeout().orElse(null),
            clientResources);

        this.clientConfiguration = clientConfiguration;
    }

    protected void constructPipeline(final SslHandler sslHandler, final ChannelPipeline pipeline) {
        final String authority = clientConfiguration.getApnsServerAddress().getHostName();

        final ApnsClientHandler apnsClientHandler;
        {
            final ApnsClientHandler.ApnsClientHandlerBuilder clientHandlerBuilder;

            if (clientConfiguration.getSigningKey().isPresent()) {
                clientHandlerBuilder = new TokenAuthenticationApnsClientHandler.TokenAuthenticationApnsClientHandlerBuilder()
                    .signingKey(clientConfiguration.getSigningKey().get())
                    .tokenExpiration(clientConfiguration.getTokenExpiration())
                    .authority(authority);
            } else {
                clientHandlerBuilder = new ApnsClientHandler.ApnsClientHandlerBuilder()
                    .authority(authority);
            }

            clientConfiguration.getFrameLogger().ifPresent(clientHandlerBuilder::frameLogger);

            apnsClientHandler = clientHandlerBuilder.build();

            clientConfiguration.getGracefulShutdownTimeout().ifPresent(timeout ->
                apnsClientHandler.gracefulShutdownTimeoutMillis(timeout.toMillis()));
        }

        clientConfiguration.getProxyHandlerFactory().ifPresent(proxyHandlerFactory ->
            pipeline.addFirst(proxyHandlerFactory.createProxyHandler()));

        pipeline.addLast(sslHandler);
        pipeline.addLast(new FlushConsolidationHandler(FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES, true));
        pipeline.addLast(new IdleStateHandler(clientConfiguration.getCloseAfterIdleDuration().toMillis(), 0, 0, TimeUnit.MILLISECONDS));
        pipeline.addLast(apnsClientHandler);
    }
}
