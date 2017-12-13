/*
 * Copyright (c) 2013-2017 Turo
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

package com.turo.pushy.apns.server;

import com.turo.pushy.apns.auth.ApnsSigningKey;
import com.turo.pushy.apns.auth.ApnsVerificationKey;
import com.turo.pushy.apns.auth.AuthenticationToken;
import com.turo.pushy.apns.auth.KeyPairUtil;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;

public class TokenAuthenticationValidatingPushNotificationHandlerTest extends ValidatingPushNotificationHandlerTest {

    private ApnsSigningKey signingKey;
    private ApnsVerificationKey verificationKey;

    private static final String KEY_ID = "key-id";
    private static final String TEAM_ID = "team-id";

    private static final AsciiString APNS_AUTHORIZATION_HEADER = new AsciiString("authorization");

    @Override
    @Before
    public void setUp() throws Exception {
        final KeyPair keyPair = KeyPairUtil.generateKeyPair();

        this.signingKey = new ApnsSigningKey(KEY_ID, TEAM_ID, (ECPrivateKey) keyPair.getPrivate());
        this.verificationKey = new ApnsVerificationKey(KEY_ID, TEAM_ID, (ECPublicKey) keyPair.getPublic());

        super.setUp();
    }

    @Override
    protected TokenAuthenticationValidatingPushNotificationHandler getHandler(final Map<String, Set<String>> deviceTokensByTopic, final Map<String, Date> expirationTimestampsByDeviceToken) {
        final Map<String, ApnsVerificationKey> verificationKeysByKeyId =
                Collections.singletonMap(KEY_ID, this.verificationKey);

        final Map<ApnsVerificationKey, Set<String>> topicsByVerificationKey =
                Collections.singletonMap(this.verificationKey, Collections.singleton(TOPIC));

        return new TokenAuthenticationValidatingPushNotificationHandler(deviceTokensByTopic, expirationTimestampsByDeviceToken, verificationKeysByKeyId, topicsByVerificationKey);
    }

    @Override
    protected void addAcceptableCredentialsToHeaders(final Http2Headers headers) throws Exception {
        final AuthenticationToken authenticationToken = new AuthenticationToken(this.signingKey, new Date());

        headers.set(APNS_AUTHORIZATION_HEADER, authenticationToken.getAuthorizationHeader());
    }

    @Test
    public void testHandleNotificationWithMissingAuthenticationToken() {
        this.headers.remove(APNS_AUTHORIZATION_HEADER);

        this.testWithExpectedRejection("Push notifications without an authentication token should be rejected",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.<String, Date>emptyMap()),
                this.headers,
                this.payload,
                RejectionReason.MISSING_PROVIDER_TOKEN);
    }

    @Test
    public void testHandleNotificationWithEmptyAuthenticationToken() {
        this.headers.set(APNS_AUTHORIZATION_HEADER, "bearer");

        this.testWithExpectedRejection("Push notifications without an authentication token should be rejected",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.<String, Date>emptyMap()),
                this.headers,
                this.payload,
                RejectionReason.MISSING_PROVIDER_TOKEN);
    }

    @Test
    public void testHandleNotificationWithBadAuthorizationHeader() {
        this.headers.set(APNS_AUTHORIZATION_HEADER, "Definitely not a legit authorization header.");

        this.testWithExpectedRejection("Push notifications without an authentication token should be rejected",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.<String, Date>emptyMap()),
                this.headers,
                this.payload,
                RejectionReason.MISSING_PROVIDER_TOKEN);
    }

    @Test
    public void testHandleNotificationWithMalformedAuthenticationToken() {
        this.headers.set(APNS_AUTHORIZATION_HEADER, "bearer Definitely not a legit token.");

        this.testWithExpectedRejection("Push notifications without an authentication token should be rejected",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.<String, Date>emptyMap()),
                this.headers,
                this.payload,
                RejectionReason.INVALID_PROVIDER_TOKEN);
    }

    @Test
    public void testHandleNotificationWithWithUnrecognizedKeyId() throws Exception {
        final KeyPair keyPair = KeyPairUtil.generateKeyPair();

        final ApnsSigningKey signingKey = new ApnsSigningKey(KEY_ID + "-UNRECOGNIZED", TEAM_ID, (ECPrivateKey) keyPair.getPrivate());
        final ApnsVerificationKey verificationKey = new ApnsVerificationKey(KEY_ID, TEAM_ID, (ECPublicKey) keyPair.getPublic());

        final Map<String, ApnsVerificationKey> verificationKeysByKeyId =
                Collections.singletonMap(KEY_ID, verificationKey);

        final Map<ApnsVerificationKey, Set<String>> topicsByVerificationKey =
                Collections.singletonMap(verificationKey, Collections.singleton(TOPIC));

        final TokenAuthenticationValidatingPushNotificationHandler handler =
                new TokenAuthenticationValidatingPushNotificationHandler(
                        DEVICE_TOKENS_BY_TOPIC, Collections.<String, Date>emptyMap(), verificationKeysByKeyId, topicsByVerificationKey);

        final AuthenticationToken authenticationToken = new AuthenticationToken(signingKey, new Date());

        this.headers.set(APNS_AUTHORIZATION_HEADER, authenticationToken.getAuthorizationHeader());

        this.testWithExpectedRejection("Push notifications with authentication tokens with unknown keys should be rejected.",
                handler,
                this.headers,
                this.payload,
                RejectionReason.INVALID_PROVIDER_TOKEN);
    }

    @Test
    public void testHandleNotificationWithWithBadSignature() throws Exception {
        final KeyPair keyPair = KeyPairUtil.generateKeyPair();

        final ApnsSigningKey unverifiedKey = new ApnsSigningKey(KEY_ID, TEAM_ID, (ECPrivateKey) keyPair.getPrivate());
        final AuthenticationToken unverifiedToken = new AuthenticationToken(unverifiedKey, new Date());

        this.headers.set(APNS_AUTHORIZATION_HEADER, unverifiedToken.getAuthorizationHeader());

        this.testWithExpectedRejection("Push notifications with an authentication token that can't be verified by the registered public key should be rejected.",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.<String, Date>emptyMap()),
                this.headers,
                this.payload,
                RejectionReason.INVALID_PROVIDER_TOKEN);
    }

    @Test
    public void testHandleNotificationWithWithExpiredAuthenticationToken() throws Exception {
        final AuthenticationToken expiredToken = new AuthenticationToken(this.signingKey, new Date(0));

        this.headers.set(APNS_AUTHORIZATION_HEADER, expiredToken.getAuthorizationHeader());

        this.testWithExpectedRejection("Push notifications with an expired authentication token should be rejected.",
                this.getHandler(DEVICE_TOKENS_BY_TOPIC, Collections.<String, Date>emptyMap()),
                this.headers,
                this.payload,
                RejectionReason.EXPIRED_PROVIDER_TOKEN);
    }

    @Test
    public void testHandleNotificationWithWithTokenForWrongTopic() throws Exception {
        final Map<String, ApnsVerificationKey> verificationKeysByKeyId =
                Collections.singletonMap(KEY_ID, this.verificationKey);

        final Map<ApnsVerificationKey, Set<String>> topicsByVerificationKey = Collections.emptyMap();

        final TokenAuthenticationValidatingPushNotificationHandler handler =
                new TokenAuthenticationValidatingPushNotificationHandler(
                        DEVICE_TOKENS_BY_TOPIC, Collections.<String, Date>emptyMap(), verificationKeysByKeyId, topicsByVerificationKey);

        final AuthenticationToken authenticationToken = new AuthenticationToken(signingKey, new Date());

        this.headers.set(APNS_AUTHORIZATION_HEADER, authenticationToken.getAuthorizationHeader());

        this.testWithExpectedRejection("Push notifications for topics not associated with a valid verification key should be rejected.",
                handler,
                this.headers,
                this.payload,
                RejectionReason.INVALID_PROVIDER_TOKEN);
    }
}
