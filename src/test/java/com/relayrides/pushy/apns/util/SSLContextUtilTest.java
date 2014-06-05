package com.relayrides.pushy.apns.util;

import static org.junit.Assert.assertNotNull;

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

import org.junit.Test;

public class SSLContextUtilTest {

	private static final String CLIENT_KEYSTORE_FILE_NAME = "/pushy-test-client.jks";
	private static final String CLIENT_EMPTY_KEYSTORE_FILE_NAME = "/empty-keystore.jks";

	private static final String CLIENT_PKCS12_FILE_NAME = "/pushy-test-client.p12";
	private static final String CLIENT_EMPTY_PKCS12_FILE_NAME = "/empty.p12";

	private static final String KEYSTORE_PASSWORD = "pushy-test";

	@Test
	public void testCreateDefaultSSLContextFromPKCS12File() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		assertNotNull(SSLContextUtil.createDefaultSSLContext(
				this.getFullPath(CLIENT_PKCS12_FILE_NAME), KEYSTORE_PASSWORD));
	}

	@Test(expected = FileNotFoundException.class)
	public void testCreateDefaultSSLContextFromPKCS12FileMissingFile() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		SSLContextUtil.createDefaultSSLContext("/path/to/non-existent-file", KEYSTORE_PASSWORD);
	}

	@Test(expected = IOException.class)
	public void testCreateDefaultSSLContextFromPKCS12FileIncorrectPassword() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		SSLContextUtil.createDefaultSSLContext(this.getFullPath(CLIENT_PKCS12_FILE_NAME), "incorrect-password");
	}

	@Test(expected = GeneralSecurityException.class)
	public void testCreateDefaultSSLContextFromPKCS12FileNullPassword() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		SSLContextUtil.createDefaultSSLContext(this.getFullPath(CLIENT_PKCS12_FILE_NAME), null);
	}

	@Test(expected = KeyStoreException.class)
	public void testCreateDefaultSSLContextFromEmptyPKCS12File() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		SSLContextUtil.createDefaultSSLContext(this.getFullPath(CLIENT_EMPTY_PKCS12_FILE_NAME), KEYSTORE_PASSWORD);
	}

	@Test
	public void testCreateDefaultSSLContextFromJKSFile() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException {
		final FileInputStream keyStoreInputStream =
				new FileInputStream(this.getFullPath(CLIENT_KEYSTORE_FILE_NAME));

		try {
			final KeyStore keyStore = KeyStore.getInstance("JKS");
			keyStore.load(keyStoreInputStream, KEYSTORE_PASSWORD.toCharArray());

			assertNotNull(SSLContextUtil.createDefaultSSLContext(keyStore, KEYSTORE_PASSWORD.toCharArray()));
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

			SSLContextUtil.createDefaultSSLContext(keyStore, "incorrect".toCharArray());
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

			SSLContextUtil.createDefaultSSLContext(keyStore, null);
		} finally {
			keyStoreInputStream.close();
		}
	}

	@Test
	public void testCreateDefaultSSLContextWithInputStream() throws IOException, CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
		final FileInputStream keyStoreInputStream =
			new FileInputStream(this.getFullPath(CLIENT_PKCS12_FILE_NAME));

		try {
			assertNotNull(SSLContextUtil.createDefaultSSLContext(keyStoreInputStream, KEYSTORE_PASSWORD));
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

			SSLContextUtil.createDefaultSSLContext(keyStore, KEYSTORE_PASSWORD.toCharArray());
		} finally {
			keyStoreInputStream.close();
		}
	}

	private String getFullPath(final String resourcePath) {
		return this.getClass().getResource(resourcePath).getPath();
	}

}
