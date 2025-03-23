package com.eatthepath.pushy.apns;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.TimeUnit;

/**
 * An APNs channel factory creates new channels connected to an APNs server. Channels constructed by this factory are
 * intended for use in an {@link ApnsChannelPool}.
 */
class ApnsChannelManagementChannelFactory extends AbstractApnsChannelFactory {

  private final ApnsClientConfiguration clientConfiguration;

  ApnsChannelManagementChannelFactory(final ApnsClientConfiguration clientConfiguration,
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

    final ApnsChannelManagementHandler apnsClientHandler;
    {
      final ApnsChannelManagementHandler.ApnsChannelManagementHandlerBuilder channelManagementHandlerBuilder;

      channelManagementHandlerBuilder = new ApnsChannelManagementHandler.ApnsChannelManagementHandlerBuilder()
          .signingKey(clientConfiguration.getSigningKey().get())
          .tokenExpiration(clientConfiguration.getTokenExpiration())
          .authority(authority);

      clientConfiguration.getFrameLogger().ifPresent(channelManagementHandlerBuilder::frameLogger);

      apnsClientHandler = channelManagementHandlerBuilder.build();

      clientConfiguration.getGracefulShutdownTimeout().ifPresent(timeout ->
          apnsClientHandler.gracefulShutdownTimeoutMillis(timeout.toMillis()));
    }

    clientConfiguration.getProxyHandlerFactory().ifPresent(proxyHandlerFactory ->
        pipeline.addFirst(proxyHandlerFactory.createProxyHandler()));

    pipeline.addLast(sslHandler);
    pipeline.addLast(apnsClientHandler);
  }
}