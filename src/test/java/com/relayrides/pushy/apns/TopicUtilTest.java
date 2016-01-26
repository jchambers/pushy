package com.relayrides.pushy.apns;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class TopicUtilTest {

    private static final String SINGLE_TOPIC_CERTIFICATE_FILE = "/single-topic-client.pem";
    private static final String MULTI_TOPIC_CERTIFICATE_FILE = "/multi-topic-client.pem";

    @Test
    public void testExtractApnsTopicsFromSingleTopicCertificate() throws Exception {
        final Certificate singleTopicCertificate = TopicUtilTest.loadCertificateFromResource(SINGLE_TOPIC_CERTIFICATE_FILE);
        final Set<String> expectedTopics = new HashSet<String>(Arrays.asList("com.relayrides.pushy"));

        assertEquals(expectedTopics, TopicUtil.extractApnsTopicsFromCertificate(singleTopicCertificate));
    }

    @Test
    public void testExtractApnsTopicsFromMultiTopicCertificate() throws Exception {
        final Certificate singleTopicCertificate = TopicUtilTest.loadCertificateFromResource(MULTI_TOPIC_CERTIFICATE_FILE);
        final Set<String> expectedTopics = new HashSet<String>(
                Arrays.asList("com.relayrides.pushy", "com.relayrides.pushy.voip", "com.relayrides.pushy.complication"));

        assertEquals(expectedTopics, TopicUtil.extractApnsTopicsFromCertificate(singleTopicCertificate));
    }

    private static Certificate loadCertificateFromResource(final String resourceName) throws CertificateException, IOException {
        try (final InputStream certificateInputStream = TopicUtilTest.class.getResourceAsStream(resourceName)) {
            final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            return certificateFactory.generateCertificate(certificateInputStream);
        }
    }
}
