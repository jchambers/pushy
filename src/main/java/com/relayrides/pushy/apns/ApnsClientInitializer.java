package com.relayrides.pushy.apns;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;

public class ApnsClientInitializer extends ChannelInitializer<SocketChannel> {
	
	private final KeyStore keyStore;
	private final char[] keyStorePassword;
	
	private static final String PROTOCOL = "TLS";
	private static final String DEFAULT_ALGORITHM = "SunX509";
	
	public ApnsClientInitializer(final KeyStore keyStore, final char[] keyStorePassword) {
		this.keyStore = keyStore;
		this.keyStorePassword = keyStorePassword;
	}
	
	@Override
	protected void initChannel(SocketChannel channel) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException {
		
		final SSLEngine sslEngine;
		{
			String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
			
	        if (algorithm == null) {
	            algorithm = DEFAULT_ALGORITHM;
	        }
	        
			final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
			trustManagerFactory.init((KeyStore) null);
			
			final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
			keyManagerFactory.init(this.keyStore, this.keyStorePassword);
			
			final SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
			sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
			
			sslEngine = sslContext.createSSLEngine();
			sslEngine.setUseClientMode(true);
		}
		
		final ChannelPipeline pipeline = channel.pipeline();
		
		pipeline.addLast("ssl", new SslHandler(sslEngine));
		pipeline.addLast("decoder", new ApnsErrorDecoder());
		pipeline.addLast("encoder", new PushNotificationEncoder());
		pipeline.addLast("handler", new ApnsErrorHandler());
	}
}
