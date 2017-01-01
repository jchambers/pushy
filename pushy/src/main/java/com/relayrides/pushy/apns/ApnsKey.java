package com.relayrides.pushy.apns;

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
abstract class ApnsKey implements ECKey {

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
