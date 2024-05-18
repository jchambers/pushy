package com.eatthepath.pushy.apns;

import io.netty.channel.EventLoopGroup;
import io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider;
import io.netty.resolver.dns.RoundRobinDnsAddressResolverGroup;
import io.netty.util.concurrent.Future;

import java.util.Objects;

public class ApnsClientResources {

  private final EventLoopGroup eventLoopGroup;
  private final RoundRobinDnsAddressResolverGroup roundRobinDnsAddressResolverGroup;

  public ApnsClientResources(final EventLoopGroup eventLoopGroup) {
    this.eventLoopGroup = Objects.requireNonNull(eventLoopGroup);

    this.roundRobinDnsAddressResolverGroup = new RoundRobinDnsAddressResolverGroup(
        ClientChannelClassUtil.getDatagramChannelClass(eventLoopGroup),
        DefaultDnsServerAddressStreamProvider.INSTANCE);
  }

  public EventLoopGroup getEventLoopGroup() {
    return eventLoopGroup;
  }

  public RoundRobinDnsAddressResolverGroup getRoundRobinDnsAddressResolverGroup() {
    return roundRobinDnsAddressResolverGroup;
  }

  public Future<?> shutdownGracefully() {
    roundRobinDnsAddressResolverGroup.close();
    return eventLoopGroup.shutdownGracefully();
  }
}
