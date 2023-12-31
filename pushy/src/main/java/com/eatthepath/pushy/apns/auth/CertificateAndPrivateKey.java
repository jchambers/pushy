package com.eatthepath.pushy.apns.auth;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Objects;

/**
 * A carrier class for certificate-and-private-key pairs used to authenticate clients to APNs servers.
 */
public class CertificateAndPrivateKey {

  private final X509Certificate certificate;
  private final PrivateKey privateKey;

  public CertificateAndPrivateKey(final X509Certificate certificate, final PrivateKey privateKey) {
    this.certificate = certificate;
    this.privateKey = privateKey;
  }

  /**
   * Returns the X.509 certificate in this certificate/private key pair.
   *
   * @return the X.509 certificate in this certificate/private key pair
   */
  public X509Certificate getCertificate() {
    return certificate;
  }

  /**
   * Returns the private key in this certificate/private key pair.
   *
   * @return the private key in this certificate/private key pair
   */
  public PrivateKey getPrivateKey() {
    return privateKey;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final CertificateAndPrivateKey that = (CertificateAndPrivateKey) o;
    return Objects.equals(certificate, that.certificate) && Arrays.equals(privateKey.getEncoded(), that.privateKey.getEncoded());
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hash(certificate) + Arrays.hashCode(privateKey.getEncoded());
  }

  /**
   * Loads a certificate and private key from the given PKCS#12 file. If more than one certificate/key pair is found in
   * the given file, which one is loaded is undefined.
   *
   * @param p12File the file from which to laod a certificate and private key
   * @param p12Password the password to be used to decrypt the PKCS#12 file
   *
   * @return a certificate and private key loaded from the given PKCS#12 file
   *
   * @throws IOException if the given file could not be read for any reason
   * @throws KeyStoreException if the given file could be read, but a certificate/key could not be loaded for any other
   * reason
   */
  public static CertificateAndPrivateKey fromP12File(final File p12File, final String p12Password) throws IOException, KeyStoreException {
    try (final InputStream p12InputStream = Files.newInputStream(p12File.toPath())) {
      return fromP12InputStream(p12InputStream, p12Password);
    }
  }

  /**
   * Loads a certificate and private key from the given PKCS#12 input stream. If more than one certificate/key pair is
   * found in the given input stream, which one is loaded is undefined.
   *
   * @param p12InputStream the input stream from which to laod a certificate and private key
   * @param p12Password the password to be used to decrypt the PKCS#12 input stream
   *
   * @return a certificate and private key loaded from the given PKCS#12 input stream
   *
   * @throws IOException if the given input stream could not be read for any reason
   * @throws KeyStoreException if the given input stream could be read, but a certificate/key could not be loaded for
   * any other reason
   */
  public static CertificateAndPrivateKey fromP12InputStream(final InputStream p12InputStream, final String p12Password) throws IOException, KeyStoreException {
    final KeyStore.PrivateKeyEntry privateKeyEntry = P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, p12Password);

    final Certificate certificate = privateKeyEntry.getCertificate();

    if (!(certificate instanceof X509Certificate)) {
      throw new KeyStoreException("Found a certificate in the provided PKCS#12 file, but it was not an X.509 certificate.");
    }

    return new CertificateAndPrivateKey((X509Certificate) certificate, privateKeyEntry.getPrivateKey());
  }
}
