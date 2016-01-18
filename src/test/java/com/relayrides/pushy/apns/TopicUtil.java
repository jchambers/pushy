package com.relayrides.pushy.apns;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;

public class TopicUtil {
    private static final String TOPIC_OID = "1.2.840.113635.100.6.3.6";

    public static Set<String> extractApnsTopicsFromCertificate(final Certificate certificate) throws IOException {
        final Set<String> topics = new HashSet<String>();

        if (certificate instanceof X509Certificate) {
            final X509Certificate x509Certificate = (X509Certificate) certificate;

            for (final String keyValuePair : x509Certificate.getSubjectX500Principal().getName().split(",")) {
                if (keyValuePair.toLowerCase().startsWith("uid=")) {
                    topics.add(keyValuePair.substring(4));
                    break;
                }
            }

            final byte[] topicExtensionData = x509Certificate.getExtensionValue(TOPIC_OID);

            if (topicExtensionData != null) {
                final ASN1Primitive extensionValue =
                        JcaX509ExtensionUtils.parseExtensionValue(topicExtensionData);

                if (extensionValue instanceof ASN1Sequence) {
                    final ASN1Sequence sequence = (ASN1Sequence) extensionValue;

                    for (int i = 0; i < sequence.size(); i++) {
                        if (sequence.getObjectAt(i) instanceof ASN1String) {
                            topics.add(sequence.getObjectAt(i).toString());
                        }
                    }
                }
            }
        }

        return topics;
    }
}
