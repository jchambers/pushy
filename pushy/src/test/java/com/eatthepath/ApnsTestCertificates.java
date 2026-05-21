package com.eatthepath;

import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

public class ApnsTestCertificates {

  private final X509Bundle caBundle;
  private final X509Bundle trustedServerCertificateBundle;
  private final X509Bundle untrustedServerCertificateBundle;
  private final X509Bundle singleTopicClientCertificateBundle;
  private final X509Bundle multiTopicClientCertificateBundle;

  // This is the value OpenSSL encodes for `ASN1:SEQUENCE:apns_topics`, where `apns_topics` is defined as:
  //
  // [ apns_topics ]
  // aps_topics.0 = UTF8String:com.eatthepath.pushy
  // aps_topics.1 = SEQWRAP,UTF8String:app
  // aps_topics.2 = UTF8String:com.eatthepath.pushy.voip
  // aps_topics.3 = SEQWRAP,UTF8String:voip
  // aps_topics.4 = UTF8String:com.eatthepath.pushy.complication
  // aps_topics.5 = SEQWRAP,UTF8String:complication
  private static final byte[] MULTI_TOPIC_EXTENSION_VALUE = Base64.getDecoder()
      .decode("BHUwcwwUY29tLmVhdHRoZXBhdGgucHVzaHkwBQwDYXBwDBljb20uZWF0dGhlcGF0aC5wdXNoeS52b2lwMAYMBHZvaXAMIWNvbS5lYXR0aGVwYXRoLnB1c2h5LmNvbXBsaWNhdGlvbjAODAxjb21wbGljYXRpb24=");

  public ApnsTestCertificates() throws Exception {
    final Instant now = Instant.now();

    final CertificateBuilder rootCertificateBuilderTemplate = new CertificateBuilder()
        .notBefore(now)
        .notAfter(now.plus(Duration.ofHours(1)));

    caBundle = rootCertificateBuilderTemplate.copy()
        .subject("CN=PushyTestRoot")
        .setKeyUsage(true, CertificateBuilder.KeyUsage.digitalSignature, CertificateBuilder.KeyUsage.keyCertSign)
        .setIsCertificateAuthority(true)
        .buildSelfSigned();

    final CertificateBuilder serverCertificateBuilderTemplate = rootCertificateBuilderTemplate.copy()
        .subject("CN=com.eatthepath.pushy")
        .setKeyUsage(true, CertificateBuilder.KeyUsage.digitalSignature, CertificateBuilder.KeyUsage.keyEncipherment)
        .addExtendedKeyUsage(CertificateBuilder.ExtendedKeyUsage.PKIX_KP_CLIENT_AUTH)
        .addExtendedKeyUsage(CertificateBuilder.ExtendedKeyUsage.PKIX_KP_SERVER_AUTH)
        .setIsCertificateAuthority(false);

    trustedServerCertificateBundle = serverCertificateBuilderTemplate.copy()
        .addSanDnsName("localhost")
        .buildIssuedBy(caBundle);

    untrustedServerCertificateBundle = serverCertificateBuilderTemplate.copy()
        .buildIssuedBy(caBundle);

    final CertificateBuilder clientCertificateBuilderTemplate = rootCertificateBuilderTemplate.copy()
        .subject("CN=Apple Push Services: com.eatthepath.pushy, UID=com.eatthepath.pushy")
        .setKeyUsage(true, CertificateBuilder.KeyUsage.digitalSignature)
        .addExtendedKeyUsage(CertificateBuilder.ExtendedKeyUsage.PKIX_KP_CLIENT_AUTH)
        .setIsCertificateAuthority(false);

    singleTopicClientCertificateBundle = clientCertificateBuilderTemplate.copy()
        .buildIssuedBy(caBundle);

    multiTopicClientCertificateBundle = clientCertificateBuilderTemplate.copy()
        .addExtensionOctetString("1.2.840.113635.100.6.3.6", false, MULTI_TOPIC_EXTENSION_VALUE)
        .buildIssuedBy(caBundle);
  }

  public X509Bundle getCaBundle() {
    return caBundle;
  }

  public X509Bundle getTrustedServerCertificateBundle() {
    return trustedServerCertificateBundle;
  }

  public X509Bundle getUntrustedServerCertificateBundle() {
    return untrustedServerCertificateBundle;
  }

  public X509Bundle getSingleTopicClientCertificateBundle() {
    return singleTopicClientCertificateBundle;
  }

  public X509Bundle getMultiTopicClientCertificateBundle() {
    return multiTopicClientCertificateBundle;
  }
}
