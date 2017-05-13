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

package com.turo.pushy.apns.auth;

import java.security.interfaces.ECKey;
import java.security.spec.ECParameterSpec;
import java.util.Objects;

/**
 * A key used for signing or verifying APNs authentication tokens. APNs keys are elliptic curve keys with some
 * additional metadata. Keys are issued to a specific team (which has a ten-character, alphanumeric identifier issued by
 * Apple), and may be used to sign or verify all APNs topics that belong to that team. Keys also have Apple-issued
 * identifiers that are unique within the scope of the team to which they have been issued, but are not necessarily
 * globally unique.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.10
 */
public abstract class ApnsKey implements ECKey {

    private final String teamId;
    private final String keyId;

    private final ECKey key;

    public static final String APNS_SIGNATURE_ALGORITHM = "SHA256withECDSA";

    /**
     * Constructs a new APNs key with the given identifiers and underlying elliptic curve key.
     *
     * @param keyId the Apple-issued, ten-digit alphanumeric identifier for this key; must not be {@code null}
     * @param teamId the Apple-issued, ten-digit alphanumeric identifier for the team that owns this key; must not be
     * {@code null}
     * @param key the underlying elliptic curve key for this APNs key
     */
    public ApnsKey(final String keyId, final String teamId, final ECKey key) {
        Objects.requireNonNull(keyId, "Key identifier must not be null.");
        Objects.requireNonNull(teamId, "Team identifier must not be null.");
        Objects.requireNonNull(key, "Key must not be null.");

        this.keyId = keyId;
        this.teamId = teamId;

        this.key = key;
    }

    /**
     * Returns the Apple-issued identifier for this key.
     *
     * @return the Apple-issued identifier for this key
     */
    public String getKeyId() {
        return this.keyId;
    }

    /**
     * Returns the Apple-issued identifier for the team that owns this key.
     *
     * @return the Apple-issued identifier for the team that owns this key
     */
    public String getTeamId() {
        return this.teamId;
    }

    /**
     * Returns the underlying elliptic curve key for this key.
     *
     * @return the underlying elliptic curve key for this key
     */
    protected ECKey getKey() {
        return this.key;
    }

    @Override
    public ECParameterSpec getParams() {
        return this.key.getParams();
    }
}
