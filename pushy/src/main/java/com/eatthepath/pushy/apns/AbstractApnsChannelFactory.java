package com.eatthepath.pushy.apns;

import com.eatthepath.pushy.apns.proxy.ProxyHandlerFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An APNs channel factory creates new channels connected to an APNs server. Channels constructed by this factory are
 * intended for use in an {@link ApnsChannelPool}.
 */
abstract class AbstractApnsChannelFactory implements PooledObjectFactory<Channel>, Closeable {

  private final SslContext sslContext;
  private final AtomicBoolean hasReleasedSslContext = new AtomicBoolean(false);

  private final AddressResolverGroup<? extends SocketAddress> addressResolverGroup;

  private final Bootstrap bootstrapTemplate;

  private final AtomicLong currentDelaySeconds = new AtomicLong(0);

  private static final long MIN_CONNECT_DELAY_SECONDS = 1;
  private static final long MAX_CONNECT_DELAY_SECONDS = 60;

  static final AttributeKey<Promise<Channel>> CHANNEL_READY_PROMISE_ATTRIBUTE_KEY =
      AttributeKey.valueOf(ApnsNotificationChannelFactory.class, "channelReadyPromise");

  AbstractApnsChannelFactory(final InetSocketAddress serverAddress,
                             final SslContext sslContext,
                             final ProxyHandlerFactory proxyHandlerFactory,
                             final boolean hostnameVerificationEnabled,
                             final Duration connectionTimeout,
                             final ApnsClientResources clientResources) {

    this.sslContext = sslContext;

    if (this.sslContext instanceof ReferenceCounted) {
      ((ReferenceCounted) this.sslContext).retain();
    }

    this.addressResolverGroup = proxyHandlerFactory != null
        ? NoopAddressResolverGroup.INSTANCE
        : clientResources.getRoundRobinDnsAddressResolverGroup();

    this.bootstrapTemplate = new Bootstrap();
    this.bootstrapTemplate.group(clientResources.getEventLoopGroup());
    this.bootstrapTemplate.option(ChannelOption.TCP_NODELAY, true);
    this.bootstrapTemplate.remoteAddress(serverAddress);
    this.bootstrapTemplate.resolver(this.addressResolverGroup);

    if (connectionTimeout != null) {
      this.bootstrapTemplate.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectionTimeout.toMillis());
    }

    this.bootstrapTemplate.handler(new ChannelInitializer<SocketChannel>() {

      @Override
      protected void initChannel(final SocketChannel channel) {
        final String authority = serverAddress.getHostName();
        final SslHandler sslHandler = sslContext.newHandler(channel.alloc(), authority, serverAddress.getPort());

        if (hostnameVerificationEnabled) {
          final SSLEngine sslEngine = sslHandler.engine();
          final SSLParameters sslParameters = sslEngine.getSSLParameters();
          sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
          sslEngine.setSSLParameters(sslParameters);
        }

        constructPipeline(sslHandler, channel.pipeline());
      }
    });
  }

  protected abstract void constructPipeline(final SslHandler sslHandler, final ChannelPipeline pipeline);

  /**
   * Creates and connects a new channel. The initial connection attempt may be delayed to accommodate exponential
   * back-off requirements.
   *
   * @param channelReadyPromise the promise to be notified when a channel has been created and connected to the APNs
   * server
   *
   * @return a future that will be notified once a channel has been created and connected to the APNs server
   */
  @Override
  public Future<Channel> create(final Promise<Channel> channelReadyPromise) {
    final long delay = this.currentDelaySeconds.get();

    channelReadyPromise.addListener(future -> {
      final long updatedDelay = future.isSuccess() ? 0 :
          Math.max(Math.min(delay * 2, MAX_CONNECT_DELAY_SECONDS), MIN_CONNECT_DELAY_SECONDS);

      this.currentDelaySeconds.compareAndSet(delay, updatedDelay);
    });


    this.bootstrapTemplate.config().group().schedule(() -> {
      final Bootstrap bootstrap = this.bootstrapTemplate.clone()
          .channelFactory(new AugmentingReflectiveChannelFactory<>(
              ClientChannelClassUtil.getSocketChannelClass(this.bootstrapTemplate.config().group()),
              CHANNEL_READY_PROMISE_ATTRIBUTE_KEY, channelReadyPromise));

      final ChannelFuture connectFuture = bootstrap.connect();

      connectFuture.addListener(future -> {
        if (!future.isSuccess()) {
          channelReadyPromise.tryFailure(future.cause());
        }
      });
    }, delay, TimeUnit.SECONDS);

    return channelReadyPromise;
  }

  /**
   * Destroys a channel by closing it.
   *
   * @param channel the channel to destroy
   * @param promise the promise to notify when the channel has been destroyed
   *
   * @return a future that will be notified when the channel has been destroyed
   */
  @Override
  public Future<Void> destroy(final Channel channel, final Promise<Void> promise) {
    channel.close().addListener(new PromiseNotifier<>(promise));
    return promise;
  }

  @Override
  public void close() {
    try {
      this.addressResolverGroup.close();
    } finally {
      if (this.sslContext instanceof ReferenceCounted) {
        if (this.hasReleasedSslContext.compareAndSet(false, true)) {
          ((ReferenceCounted) this.sslContext).release();
        }
      }
    }
  }
}