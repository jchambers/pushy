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

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PushManagerFactoryTest {

	protected static final ApnsEnvironment TEST_ENVIRONMENT =
			new ApnsEnvironment("127.0.0.1", 2195, "127.0.0.1", 2196);

	private static final String CLIENT_KEYSTORE_FILE_NAME = "/pushy-test-client.jks";
	private static final String CLIENT_EMPTY_KEYSTORE_FILE_NAME = "/empty-keystore.jks";

	private static final String CLIENT_PKCS12_FILE_NAME = "/pushy-test-client.p12";
	private static final String CLIENT_EMPTY_PKCS12_FILE_NAME = "/empty.p12";

	private static final String KEYSTORE_PASSWORD = "pushy-test";

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testPushManagerFactory() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		// As long as nothing explodes, we're happy
		new PushManagerFactory<ApnsPushNotification>(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient());
	}

	@Test(expected = NullPointerException.class)
	public void testPushManagerFactoryNullEnvironment() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		new PushManagerFactory<ApnsPushNotification>(null, SSLTestUtil.createSSLContextForTestClient());
	}

	@Test(expected = NullPointerException.class)
	public void testPushManagerFactoryNullSslContext() {
		new PushManagerFactory<ApnsPushNotification>(TEST_ENVIRONMENT, null);
	}

	@Test
	public void testCreateDefaultSSLContextFromPKCS12File() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		assertNotNull(PushManagerFactory.createDefaultSSLContext(
				this.getFullPath(CLIENT_PKCS12_FILE_NAME), KEYSTORE_PASSWORD));
	}

	@Test(expected = FileNotFoundException.class)
	public void testCreateDefaultSSLContextFromPKCS12FileMissingFile() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		PushManagerFactory.createDefaultSSLContext("/path/to/non-existent-file", KEYSTORE_PASSWORD);
	}

	@Test(expected = IOException.class)
	public void testCreateDefaultSSLContextFromPKCS12FileIncorrectPassword() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		PushManagerFactory.createDefaultSSLContext(this.getFullPath(CLIENT_PKCS12_FILE_NAME), "incorrect-password");
	}

	@Test(expected = GeneralSecurityException.class)
	public void testCreateDefaultSSLContextFromPKCS12FileNullPassword() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		PushManagerFactory.createDefaultSSLContext(this.getFullPath(CLIENT_PKCS12_FILE_NAME), null);
	}

	@Test(expected = KeyStoreException.class)
	public void testCreateDefaultSSLContextFromEmptyPKCS12File() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		PushManagerFactory.createDefaultSSLContext(this.getFullPath(CLIENT_EMPTY_PKCS12_FILE_NAME), KEYSTORE_PASSWORD);
	}

	@Test
	public void testCreateDefaultSSLContextFromJKSFile() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException {
		final FileInputStream keyStoreInputStream =
				new FileInputStream(this.getFullPath(CLIENT_KEYSTORE_FILE_NAME));

		try {
			final KeyStore keyStore = KeyStore.getInstance("JKS");
			keyStore.load(keyStoreInputStream, KEYSTORE_PASSWORD.toCharArray());

			assertNotNull(PushManagerFactory.createDefaultSSLContext(keyStore, KEYSTORE_PASSWORD.toCharArray()));
		} finally {
			keyStoreInputStream.close();
		}
	}

	@Test(expected = UnrecoverableKeyException.class)
	public void testCreateDefaultSSLContextFromJKSFileIncorrectPassword() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException {
		final FileInputStream keyStoreInputStream =
				new FileInputStream(this.getFullPath(CLIENT_KEYSTORE_FILE_NAME));

		try {
			final KeyStore keyStore = KeyStore.getInstance("JKS");
			keyStore.load(keyStoreInputStream, KEYSTORE_PASSWORD.toCharArray());

			PushManagerFactory.createDefaultSSLContext(keyStore, "incorrect".toCharArray());
		} finally {
			keyStoreInputStream.close();
		}
	}

	@Test(expected = GeneralSecurityException.class)
	public void testCreateDefaultSSLContextFromJKSFileNullPassword() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException {
		final FileInputStream keyStoreInputStream =
				new FileInputStream(this.getFullPath(CLIENT_KEYSTORE_FILE_NAME));

		try {
			final KeyStore keyStore = KeyStore.getInstance("JKS");
			keyStore.load(keyStoreInputStream, KEYSTORE_PASSWORD.toCharArray());

			PushManagerFactory.createDefaultSSLContext(keyStore, null);
		} finally {
			keyStoreInputStream.close();
		}
	}

	@Test
	public void testCreateDefaultSSLContextWithInputStream() throws IOException, CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
		final FileInputStream keyStoreInputStream =
			new FileInputStream(this.getFullPath(CLIENT_PKCS12_FILE_NAME));

		try {
			assertNotNull(PushManagerFactory.createDefaultSSLContext(keyStoreInputStream, KEYSTORE_PASSWORD));
		} finally {
			keyStoreInputStream.close();
		}
	}

	@Test(expected = KeyStoreException.class)
	public void testCreateDefaultSSLContextFromEmptyJKSFile() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException, KeyManagementException {
		final FileInputStream keyStoreInputStream =
				new FileInputStream(this.getFullPath(CLIENT_EMPTY_KEYSTORE_FILE_NAME));

		try {
			final KeyStore keyStore = KeyStore.getInstance("JKS");
			keyStore.load(keyStoreInputStream, KEYSTORE_PASSWORD.toCharArray());

			PushManagerFactory.createDefaultSSLContext(keyStore, KEYSTORE_PASSWORD.toCharArray());
		} finally {
			keyStoreInputStream.close();
		}
	}

	private String getFullPath(final String resourcePath) {
		return this.getClass().getResource(resourcePath).getPath();
	}

}
