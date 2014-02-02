package com.relayrides.pushy.apns;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

class SSLUtil {

	private static final String PROTOCOL = "TLS";
	private static final String DEFAULT_ALGORITHM = "SunX509";

	// The keystore was generated with the following command:
	// keytool -genkey -alias pushy-test -keysize 2048 -validity 36500 -keyalg RSA -dname "CN=pushy-test" -keypass pushy-test -storepass pushy-test -keystore pushy-test.jks
	private static final String KEYSTORE_FILE_NAME = "/pushy-test.jks";
	private static final char[] KEYSTORE_PASSWORD = "pushy-test".toCharArray();

	public static SSLEngine createMockServerSSLEngine() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException, KeyManagementException {

		final InputStream keyStoreInputStream = SSLUtil.class.getResourceAsStream(KEYSTORE_FILE_NAME);

		final KeyStore keyStore = KeyStore.getInstance("JKS");
		keyStore.load(keyStoreInputStream, KEYSTORE_PASSWORD);

		String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");

		if (algorithm == null) {
			algorithm = DEFAULT_ALGORITHM;
		}

		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
		keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD);

		// Initialize the SSLContext to work with our key managers.
		final SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
		sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

		final SSLEngine sslEngine = sslContext.createSSLEngine();
		sslEngine.setUseClientMode(false);

		return sslEngine;
	}
}
