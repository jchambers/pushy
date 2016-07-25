package com.relayrides.pushy.apns;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.SucceededFuture;

/**
 * <p>A mock APNs server emulates the behavior of a real APNs server (but doesn't actually deliver notifications to
 * their destinations). Mock servers are primarily useful for integration tests and benchmarks; most users will
 * <strong>not</strong> need to interact with mock servers.</p>
 *
 * <p>Mock servers maintain a registry of tokens for a variety of topics. When first created, no tokens are registered
 * with a mock server, and all attempts to send notifications will fail until at least one token is registered via the
 * {@link com.relayrides.pushy.apns.MockApnsServer#addToken(String, String, Date)} method.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.8
 */
public class MockApnsServer {

    private final ServerBootstrap bootstrap;
    private final boolean shouldShutDownEventLoopGroup;

    private final Map<String, Map<String, Date>> tokenExpirationsByTopic = new HashMap<>();

    private ChannelGroup allChannels;

    private boolean emulateInternalErrors = false;

    protected MockApnsServer(final SslContext sslContext, final EventLoopGroup eventLoopGroup) {
        this.bootstrap = new ServerBootstrap();

        if (eventLoopGroup != null) {
            this.bootstrap.group(eventLoopGroup);
            this.shouldShutDownEventLoopGroup = false;
        } else {
            this.bootstrap.group(new NioEventLoopGroup(1));
            this.shouldShutDownEventLoopGroup = true;
        }

        this.bootstrap.channel(SocketChannelClassUtil.getServerSocketChannelClass(this.bootstrap.config().group()));
        this.bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(final SocketChannel channel) throws Exception {
                final
                SslHandler sslHandler = sslContext.newHandler(channel.alloc());
                channel.pipeline().addLast(sslHandler);
                channel.pipeline().addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {

                    @Override
                    protected void configurePipeline(final ChannelHandlerContext context, final String protocol) throws Exception {
                        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                            context.pipeline().addLast(new MockApnsServerHandler.MockApnsServerHandlerBuilder()
                                    .apnsServer(MockApnsServer.this)
                                    .initialSettings(new Http2Settings().maxConcurrentStreams(8))
                                    .build());

                            MockApnsServer.this.allChannels.add(context.channel());
                        } else {
                            throw new IllegalStateException("Unexpected protocol: " + protocol);
                        }
                    }
                });
            }
        });
    }

    /**
     * Starts this mock server and listens for traffic on the given port.
     *
     * @param port the port to which this server should bind
     *
     * @return a {@code Future} that will succeed when the server has bound to the given port and is ready to accept
     * traffic
     */
    public Future<Void> start(final int port) {
        final ChannelFuture channelFuture = this.bootstrap.bind(port);

        this.allChannels = new DefaultChannelGroup(channelFuture.channel().eventLoop(), true);
        this.allChannels.add(channelFuture.channel());

        return channelFuture;
    }

    /**
     * Registers a new token for a specific topic. Registered tokens may have an expiration date; attempts to send
     * notifications to tokens with expiration dates in the past will fail.
     *
     * @param topic the topic for which to register the given token
     * @param token the token to register
     * @param expiration the time at which the token expires (or expired); may be {@code null}, in which case the token
     * never expires
     */
    public void addToken(final String topic, final String token, final Date expiration) {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(token);

        if (!this.tokenExpirationsByTopic.containsKey(topic)) {
            this.tokenExpirationsByTopic.put(topic, new HashMap<String, Date>());
        }

        this.tokenExpirationsByTopic.get(topic).put(token, expiration);
    }

    /**
     * Unregisters all tokens from this server.
     */
    public void clearTokens() {
        this.tokenExpirationsByTopic.clear();
    }

    protected boolean isTokenRegisteredForTopic(final String token, final String topic) {
        final Map<String, Date> tokensWithinTopic = this.tokenExpirationsByTopic.get(topic);

        return tokensWithinTopic != null && tokensWithinTopic.containsKey(token);
    }

    protected Date getExpirationTimestampForTokenInTopic(final String token, final String topic) {
        final Map<String, Date> tokensWithinTopic = this.tokenExpirationsByTopic.get(topic);

        return tokensWithinTopic != null ? tokensWithinTopic.get(token) : null;
    }

    protected void setEmulateInternalErrors(final boolean emulateInternalErrors) {
        this.emulateInternalErrors = emulateInternalErrors;
    }

    protected boolean shouldEmulateInternalErrors() {
        return this.emulateInternalErrors;
    }

    /**
     * <p>Shuts down this server and releases the port to which this server was bound. If a {@code null} event loop
     * group was provided at construction time, the server will also shut down its internally-managed event loop
     * group.</p>
     *
     * <p>If a non-null {@code EventLoopGroup} was provided at construction time, mock servers may be reconnected and
     * reused after they have been shut down. If no event loop group was provided at construction time, mock servers may
     * not be restarted after they have been shut down via this method.</p>
     *
     * @return a {@code Future} that will succeed once the server has finished unbinding from its port and, if the
     * server was managing its own event loop group, its event loop group has shut down
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Future<Void> shutdown() {
        final Future<Void> channelCloseFuture = (this.allChannels != null) ?
                this.allChannels.close() : new SucceededFuture<Void>(GlobalEventExecutor.INSTANCE, null);

                final Future<Void> disconnectFuture;

                if (this.shouldShutDownEventLoopGroup) {
                    // Wait for the channel to close before we try to shut down the event loop group
                    channelCloseFuture.addListener(new GenericFutureListener<Future<Void>>() {

                        @Override
                        public void operationComplete(final Future<Void> future) throws Exception {
                            MockApnsServer.this.bootstrap.config().group().shutdownGracefully();
                        }
                    });

                    // Since the termination future for the event loop group is a Future<?> instead of a Future<Void>,
                    // we'll need to create our own promise and then notify it when the termination future completes.
                    disconnectFuture = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);

                    this.bootstrap.config().group().terminationFuture().addListener(new GenericFutureListener() {

                        @Override
                        public void operationComplete(final Future future) throws Exception {
                            assert disconnectFuture instanceof DefaultPromise;
                            ((DefaultPromise<Void>) disconnectFuture).trySuccess(null);
                        }
                    });
                } else {
                    // We're done once we've closed all the channels, so we can return the closure future directly.
                    disconnectFuture = channelCloseFuture;
                }

                return disconnectFuture;
    }
}
