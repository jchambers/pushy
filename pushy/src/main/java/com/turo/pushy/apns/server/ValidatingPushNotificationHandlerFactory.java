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

import com.turo.pushy.apns.auth.ApnsVerificationKey;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>A push notification handler factory that constructs handlers that, to the extent possible, perform the same checks
 * and validation steps as a real APNs server.</p>
 *
 * <p>Because handlers constructed by this factory try to emulate the behavior of real APNs servers, callers will need
 * to provide collections of legal device tokens and token expiration times. If clients connect TLS-based
 * authentication, handlers will derive a list of allowed topics from the client's certificate. If using token-based
 * authentication, callers will need to specify a collection of public keys and topics to which those keys apply.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @see MockApnsServerBuilder#setHandlerFactory(PushNotificationHandlerFactory)
 *
 * @since 0.12
 */
public class ValidatingPushNotificationHandlerFactory implements PushNotificationHandlerFactory {

    private final Map<String, Set<String>> deviceTokensByTopic;
    private final Map<String, Date> expirationTimestampsByDeviceToken;

    private final Map<String, ApnsVerificationKey> verificationKeysByKeyId;
    private final Map<ApnsVerificationKey, Set<String>> topicsByVerificationKey;

    /**
     * Constructs a new factory for push notification handlers that emulate the behavior of a real APNs server.
     *
     * @param deviceTokensByTopic a map of topics to the set of device tokens that may send push notifications to that
     * topic; may be {@code null}, in which case constructed handlers will reject all notifications
     * @param expirationTimestampsByDeviceToken a map of device tokens to the time at which they expire; tokens not in
     * the map (or all tokens if the map is {@code null} or empty) will never be considered "expired"
     * @param verificationKeysByKeyId a map of key identifiers to the keys with that identifier; only required for token
     * authentication, and may be {@code null}, in which case all notifications sent with token authentication will be
     * rejected
     * @param topicsByVerificationKey a map of verification keys to the set of topics for which they may verify
     * authentication tokens; only needed for token authentication, and may be {@code null} in which case all
     * notifications sent with token authentication will be rejected
     */
    public ValidatingPushNotificationHandlerFactory(final Map<String, Set<String>> deviceTokensByTopic, final Map<String, Date> expirationTimestampsByDeviceToken, final Map<String, ApnsVerificationKey> verificationKeysByKeyId, final Map<ApnsVerificationKey, Set<String>> topicsByVerificationKey) {
        this.deviceTokensByTopic = deviceTokensByTopic != null ?
                deviceTokensByTopic : Collections.<String, Set<String>>emptyMap();

        this.expirationTimestampsByDeviceToken = expirationTimestampsByDeviceToken != null ?
                expirationTimestampsByDeviceToken : Collections.<String, Date>emptyMap();

        this.verificationKeysByKeyId = verificationKeysByKeyId != null ?
                verificationKeysByKeyId : Collections.<String, ApnsVerificationKey>emptyMap();

        this.topicsByVerificationKey = topicsByVerificationKey != null ?
                topicsByVerificationKey : Collections.<ApnsVerificationKey, Set<String>>emptyMap();
    }

    @Override
    public PushNotificationHandler buildHandler(final SSLSession sslSession) {
        try {
            // This will throw an exception if the peer hasn't authenticated (i.e. we're expecting
            // token authentication).
            final String principalName = sslSession.getPeerPrincipal().getName();

            final Pattern pattern = Pattern.compile(".*UID=([^,]+).*");
            final Matcher matcher = pattern.matcher(principalName);

            final String baseTopic;

            if (matcher.matches()) {
                baseTopic = matcher.group(1);
            } else {
                throw new IllegalArgumentException("Client certificate does not specify a base topic.");
            }

            return new TlsAuthenticationValidatingPushNotificationHandler(
                    this.deviceTokensByTopic,
                    this.expirationTimestampsByDeviceToken,
                    baseTopic);

        } catch (final SSLPeerUnverifiedException e) {
            // No need for alarm; this is an expected case. If a client hasn't performed mutual TLS authentication, we
            // assume they want to use token authentication.
            return new TokenAuthenticationValidatingPushNotificationHandler(
                    this.deviceTokensByTopic,
                    this.expirationTimestampsByDeviceToken,
                    this.verificationKeysByKeyId,
                    this.topicsByVerificationKey);
        }
    }
}
