package com.relayrides.pushy.apns;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * An extremely naive TrustManager that blindly accepts all certificates. Needless to say, this should never, ever be
 * used in any kind of production environment and is only intended to support unit tests here.
 * 
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 */
final class TrustAllTrustManager implements X509TrustManager {

	public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
		// As long as we don't throw a CertificateException, we "trust" the certificate
	}

	public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
		// As long as we don't throw a CertificateException, we "trust" the certificate
	}

	public X509Certificate[] getAcceptedIssuers() {
		return new X509Certificate[0];
	}
}
