package com.relayrides.pushy.apns;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;

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

/**
 * A channel initializer that can create an SSLEngine prepared to communicate with an APNs server.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public abstract class SslCapableChannelInitializer extends ChannelInitializer<SocketChannel> {

	private static final String PROTOCOL = "TLS";
	private static final String DEFAULT_ALGORITHM = "SunX509";
	
	protected SslCapableChannelInitializer() {}
	
	protected SslHandler getSslHandler(final KeyStore keyStore, final char[] keyStorePassword) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
		String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
		
        if (algorithm == null) {
            algorithm = DEFAULT_ALGORITHM;
        }
        
		final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
		trustManagerFactory.init((KeyStore) null);
		
		final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
		keyManagerFactory.init(keyStore, keyStorePassword);
		
		final SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
		sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
		
		final SSLEngine sslEngine = sslContext.createSSLEngine();
		sslEngine.setUseClientMode(true);
		
		return new SslHandler(sslEngine);
	}
}
