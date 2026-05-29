package com.eatthepath.pushy.apns;

import io.netty.channel.IoEventLoopGroup;
import io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider;
import io.netty.resolver.dns.RoundRobinDnsAddressResolverGroup;
import io.netty.util.concurrent.Future;

import java.util.Objects;

/**
 * APNs client resources are bundles of relatively "expensive" objects (thread pools, DNS resolvers, etc.) that can be
 * shared between {@link ApnsClient} instances.
 *
 * @see ApnsClientBuilder#setApnsClientResources(ApnsClientResources)
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.16
 */
public class ApnsClientResources {

  private final IoEventLoopGroup ioEventLoopGroup;
  private final RoundRobinDnsAddressResolverGroup roundRobinDnsAddressResolverGroup;

  /**
   * Constructs a new set of client resources that uses the given default event loop group. Clients that use this
   * resource set will use the given event loop group for IO operations.
   *
   * @param ioEventLoopGroup the event loop group for this set of resources
   */
  public ApnsClientResources(final IoEventLoopGroup ioEventLoopGroup) {
    this.ioEventLoopGroup = Objects.requireNonNull(ioEventLoopGroup);

    this.roundRobinDnsAddressResolverGroup = new RoundRobinDnsAddressResolverGroup(
        ClientChannelClassUtil.getDatagramChannelClass(ioEventLoopGroup),
        DefaultDnsServerAddressStreamProvider.INSTANCE);
  }

  /**
   * Returns the event loop group for this resource set.
   *
   * @return the event loop group for this resource set
   */
  public IoEventLoopGroup getIoEventLoopGroup() {
    return ioEventLoopGroup;
  }

  /**
   * Returns the DNS resolver for this resource set.
   *
   * @return the DNS resolver for this resource set
   */
  public RoundRobinDnsAddressResolverGroup getRoundRobinDnsAddressResolverGroup() {
    return roundRobinDnsAddressResolverGroup;
  }

  /**
   * Gracefully shuts down any long-lived resources in this resource group. If callers manage their own
   * {@code ApnsClientResources} instances (as opposed to using default resources provided by {@link ApnsClientBuilder},
   * then they <em>must</em> call this method after all clients that use a given set of resources have been shut down.
   *
   * @return a future that completes once the long-lived resources in this set of resources has finished shutting down
   *
   * @see ApnsClientBuilder#setApnsClientResources(ApnsClientResources)
   */
  public Future<?> shutdownGracefully() {
    roundRobinDnsAddressResolverGroup.close();
    return ioEventLoopGroup.shutdownGracefully();
  }
}
