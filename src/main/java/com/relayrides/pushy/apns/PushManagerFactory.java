/* Copyright (c) 2014 RelayRides
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

package com.relayrides.pushy.apns;

import io.netty.channel.nio.NioEventLoopGroup;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@code PushManagerFactory} is used to configure and construct a new {@link PushManager}.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class PushManagerFactory<T extends ApnsPushNotification> {

	private static final String PROTOCOL = "TLS";
	private static final String DEFAULT_ALGORITHM = "SunX509";

	private final ApnsEnvironment environment;
	private final SSLContext sslContext;

	private int concurrentConnectionCount = 1;
	private int sentNotificationBufferCapacity = ApnsConnection.DEFAULT_SENT_NOTIFICATION_BUFFER_CAPACITY;

	private NioEventLoopGroup eventLoopGroup;
	private ExecutorService listenerExecutorService;

	private BlockingQueue<T> queue;

	private static final Logger log = LoggerFactory.getLogger(PushManagerFactory.class);

	/**
	 * Constructs a new factory that will construct {@link PushManager}s that operate in the given environment with the
	 * given credentials.
	 *
	 * @param environment the environment in which constructed {@code PushManager}s will operate
	 * @param sslContext the SSL context in which connections controlled by the constructed {@code PushManager} will
	 * operate
	 */
	public PushManagerFactory(final ApnsEnvironment environment, final SSLContext sslContext) {

		if (environment == null) {
			throw new NullPointerException("APNs environment must not be null.");
		}

		if (sslContext == null) {
			throw new NullPointerException("SSL context must not be null.");
		}

		this.environment = environment;
		this.sslContext = sslContext;
	}

	/**
	 * <p>Sets the number of concurrent connections constructed {@code PushManagers} should maintain to the APNs
	 * gateway. By default, constructed {@code PushManagers} will maintain a single connection to the gateway.</p>
	 *
	 * @param concurrentConnectionCount the number of parallel connections to maintain
	 *
	 * @return a reference to this factory for ease of chaining configuration calls
	 */
	public PushManagerFactory<T> setConcurrentConnectionCount(final int concurrentConnectionCount) {
		this.concurrentConnectionCount = concurrentConnectionCount;
		return this;
	}

	/**
	 * <p>Sets a custom event loop group to be used by constructed {@code PushMangers}. If {@code null}, constructed
	 * {@code PushManagers} will be create and maintain their own event loop groups. If a non-{@code null} event loop
	 * group is provided, callers <strong>must</strong> shut down the event loop group after shutting down all
	 * {@code PushManager} instances that use that event loop group.</p>
	 *
	 * <p>By default, constructed {@code PushManagers} will construct and maintain their own event loop groups.</p>
	 *
	 * @param eventLoopGroup the event loop group constructed {@code PushManagers} should use for their connections to
	 * the APNs gateway and feedback service; if not {@code null}, the caller <strong>must</strong> shut
	 * down the event loop group after shutting down all push managers that use the group
	 *
	 * @return a reference to this factory for ease of chaining configuration calls
	 */
	public PushManagerFactory<T> setEventLoopGroup(final NioEventLoopGroup eventLoopGroup) {
		this.eventLoopGroup = eventLoopGroup;
		return this;
	}

	/**
	 * <p>Sets a custom executor service to be used by constructed {@code PushManagers} to dispatch notifications to
	 * registered listeners. If {@code null}, constructed {@code PushManager} instances will create and maintain their
	 * own executor services. If a non-{@code null} executor service is provided, callers <strong>must</strong> shut
	 * down the executor service after shutting down all {@code PushManager} instances that use that executor service.</p>
	 * 
	 * <p>By default, constructed {@code PushManagers} will construct and maintain their own executor services.</p>
	 * 
	 * @param listenerExecutorService the executor service to be used by constructed {@code PushManager} instances to
	 * dispatch notifications to registered listeners; if not {@code null}, the caller <strong>must</strong> shut down
	 * the executor service after shutting down all push managers that use the executor service
	 * 
	 * @return a reference to this factory for ease of chaining configuration calls
	 */
	public PushManagerFactory<T> setListenerExecutorService(final ExecutorService listenerExecutorService) {
		this.listenerExecutorService = listenerExecutorService;
		return this;
	}

	/**
	 * <p>Sets the queue to be used to pass new notifications to constructed {@code PushManagers}. If {@code null} (the
	 * default), constructed push managers will construct their own queues.</p>
	 *
	 * @param queue the queue to be used to pass new notifications to constructed push managers
	 * 
	 * @return a reference to this factory for ease of chaining configuration calls
	 */
	public PushManagerFactory<T> setQueue(final BlockingQueue<T> queue) {
		this.queue = queue;
		return this;
	}

	/**
	 * Sets the capacity of the notification buffers for connections created by constructed {@code PushManagers}. By
	 * default, the capacity of sent notification buffers is
	 * {@value ApnsConnection#DEFAULT_SENT_NOTIFICATION_BUFFER_CAPACITY}; while sent notification buffers may have any
	 * positive capacity, it is not recommended that they be given a capacity less than the default.
	 * 
	 * @param sentNotificationBufferCapacity the capacity of sent notification buffers for connections created by
	 * constructed push managers
	 * 
	 * @return a reference to this factory for ease of chaining configuration calls
	 */
	public PushManagerFactory<T> setSentNotificationBufferCapacity(final int sentNotificationBufferCapacity) {
		this.sentNotificationBufferCapacity = sentNotificationBufferCapacity;
		return this;
	}

	/**
	 * <p>Constructs a new {@link PushManager} with the settings provided to this factory. The returned push manager
	 * will not be started automatically.</p>
	 *
	 * @return a new, configured {@code PushManager}
	 */
	public PushManager<T> buildPushManager() {
		return new PushManager<T>(
				this.environment,
				this.sslContext,
				this.concurrentConnectionCount,
				this.eventLoopGroup,
				this.listenerExecutorService,
				this.queue,
				this.sentNotificationBufferCapacity);
	}

	/**
	 * Creates a new SSL context using the JVM default trust managers and the certificates in the given PKCS12 file.
	 *
	 * @param pathToPKCS12File the path to a PKCS12 file that contains the client certificate
	 * @param keystorePassword the password to read the PKCS12 file; may be {@code null}
	 *
	 * @return an SSL context configured with the given client certificate and the JVM default trust managers
	 */
	public static SSLContext createDefaultSSLContext(final String pathToPKCS12File, final String keystorePassword) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException, IOException {
		final FileInputStream keystoreInputStream = new FileInputStream(pathToPKCS12File);
		return createDefaultSSLContext(keystoreInputStream, keystorePassword);
	}

	/**
	 * Creates a new SSL context using the JVM default trust managers and the certificates in the given PKCS12 InputStream.
	 *
	 * @param keystoreInputStream a PKCS12 file that contains the client certificate
	 * @param keystorePassword the password to read the PKCS12 file; may be {@code null}
	 *
	 * @return an SSL context configured with the given client certificate and the JVM default trust managers
	 */
	public static SSLContext createDefaultSSLContext(final InputStream keystoreInputStream, final String keystorePassword) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException, IOException {
		try {
			final KeyStore keyStore = KeyStore.getInstance("PKCS12");
			keyStore.load(keystoreInputStream, keystorePassword != null ? keystorePassword.toCharArray() : null);
			return PushManagerFactory.createDefaultSSLContext(keyStore, keystorePassword != null ? keystorePassword.toCharArray() : null);
		} finally {
			try {
				keystoreInputStream.close();
			} catch (IOException e) {
				log.error("Failed to close keystore input stream.", e);
			}
		}
	}

	/**
	 * Creates a new SSL context using the JVM default trust managers and the certificates in the given keystore.
	 *
	 * @param keyStore A {@code KeyStore} containing the client certificates to present during a TLS handshake; may be
	 * {@code null} if the environment does not require TLS. The {@code KeyStore} should be loaded before being used
	 * here.
	 * @param keyStorePassword a password to unlock the given {@code KeyStore}; may be {@code null}
	 *
	 * @return an SSL context configured with the certificates in the given keystore and the JVM default trust managers
	 */
	public static SSLContext createDefaultSSLContext(final KeyStore keyStore, final char[] keyStorePassword) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException {
		String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");

		if (algorithm == null) {
			algorithm = DEFAULT_ALGORITHM;
		}

		if (keyStore.size() == 0) {
			throw new KeyStoreException("Keystore is empty; while this is legal for keystores in general, APNs clients must have at least one key.");
		}

		final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
		trustManagerFactory.init((KeyStore) null);

		final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
		keyManagerFactory.init(keyStore, keyStorePassword);

		final SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
		sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

		return sslContext;
	}
}
