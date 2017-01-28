package com.relayrides.pushy.apns;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;

public class ApnsVerificationKeyRegistry extends ApnsKeyRegistry<ApnsVerificationKey> {

    /**
     * Registers a public key for verifying authentication tokens for the given topics. Clears any keys and topics
     * previously associated with the given team.
     *
     * @param publicKey a public key to be used to verify authentication tokens
     * @param teamId an identifier for the team to which the given public key belongs
     * @param keyId an identifier for the given public key
     * @param topics the topics belonging to the given team for which the given public key can be used to verify
     * authentication tokens
     *
     * @throws NoSuchAlgorithmException if the required signing algorithm is not available
     * @throws InvalidKeyException if the given key is invalid for any reason
     *
     * @since 0.9
     */
    public void registerKey(final ECPublicKey publicKey, final String teamId, final String keyId, final Collection<String> topics) throws NoSuchAlgorithmException, InvalidKeyException {
        this.registerKey(publicKey, teamId, keyId, topics.toArray(new String[0]));
    }

    /**
     * Registers a public key for verifying authentication tokens for the given topics. Clears any keys and topics
     * previously associated with the given team.
     *
     * @param publicKey a public key to be used to verify authentication tokens
     * @param teamId an identifier for the team to which the given public key belongs
     * @param keyId an identifier for the given public key
     * @param topics the topics belonging to the given team for which the given public key can be used to verify
     * authentication tokens
     *
     * @throws NoSuchAlgorithmException if the required signing algorithm is not available
     * @throws InvalidKeyException if the given key is invalid for any reason
     *
     * @since 0.9
     */
    public void registerKey(final ECPublicKey publicKey, final String teamId, final String keyId, final String... topics) throws NoSuchAlgorithmException, InvalidKeyException {
        this.registerKey(new ApnsVerificationKey(keyId, teamId, publicKey), topics);
    }

    @Override
    protected ApnsVerificationKey loadKey(final InputStream keyInputStream, final String teamId, final String keyId) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        final ECPublicKey publicKey;
        {
            final String base64EncodedPublicKey;
            {
                final StringBuilder publicKeyBuilder = new StringBuilder();

                final BufferedReader reader = new BufferedReader(new InputStreamReader(keyInputStream));
                boolean haveReadHeader = false;
                boolean haveReadFooter = false;

                for (String line; (line = reader.readLine()) != null; ) {
                    if (!haveReadHeader) {
                        if (line.contains("BEGIN PUBLIC KEY")) {
                            haveReadHeader = true;
                            continue;
                        }
                    } else {
                        if (line.contains("END PUBLIC KEY")) {
                            haveReadFooter = true;
                            break;
                        } else {
                            publicKeyBuilder.append(line);
                        }
                    }
                }

                if (!(haveReadHeader && haveReadFooter)) {
                    throw new IOException("Could not find public key header/footer");
                }

                base64EncodedPublicKey = publicKeyBuilder.toString();
            }

            final ByteBuf wrappedEncodedPublicKey = Unpooled.wrappedBuffer(base64EncodedPublicKey.getBytes(StandardCharsets.US_ASCII));

            try {
                final ByteBuf decodedPublicKey = Base64.decode(wrappedEncodedPublicKey);

                try {
                    final byte[] keyBytes = new byte[decodedPublicKey.readableBytes()];
                    decodedPublicKey.readBytes(keyBytes);

                    final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
                    final KeyFactory keyFactory = KeyFactory.getInstance("EC");
                    publicKey = (ECPublicKey) keyFactory.generatePublic(keySpec);
                } catch (final InvalidKeySpecException e) {
                    throw new InvalidKeyException(e);
                } finally {
                    decodedPublicKey.release();
                }
            } finally {
                wrappedEncodedPublicKey.release();
            }
        }

        return new ApnsVerificationKey(keyId, teamId, publicKey);
    }
}
