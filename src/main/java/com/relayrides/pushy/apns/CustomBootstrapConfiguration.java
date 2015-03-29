package com.relayrides.pushy.apns;

import io.netty.bootstrap.Bootstrap;

/**
 *  This interface aims at providing a callback so that a previously
 *  instantiated bootstrap object can be configured prior to its use.
 *  @author sylvere richard
 */
public interface CustomBootstrapConfiguration {
	/**
	 * <p>Put all your custom bootstrap configuration in this method.</p>
	 * <p>Example to configure a NameResolver as of Netty 4.1:
	 * <pre>{@code
CustomBootstrapConfiguration customConfig = new CustomBootstrapConfiguration() {
   @Override
   public void customConfiguration(Bootstrap bootstrap) {
     bootstrap.resolver(new NameResolverGroup<InetSocketAddress>() {
	   @Override
	   protected NameResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
	     return (NameResolver<InetSocketAddress>) new DefaultNameResolver(executor) {
		    @Override
		    public boolean isSupported(SocketAddress address) {
		       InetSocketAddress add = (InetSocketAddress) address;
		       return add.getHostName().contains("apple.com") == false;
		    }
	     };
	   }
     });
   }
}
	}</pre>
	 * </p>
	 * @param bootstrap The bootstrap object to configure.
	 */
	public void customConfiguration(Bootstrap bootstrap);
}
