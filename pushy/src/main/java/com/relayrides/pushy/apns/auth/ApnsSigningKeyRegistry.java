package com.relayrides.pushy.apns.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;

public class ApnsSigningKeyRegistry extends ApnsKeyRegistry<ApnsSigningKey> {
    /**
     * <p>Registers a private signing key for the given topics. Clears any topics and keys previously associated with
     * the given team.</p>
     *
     * <p>Callers <em>must</em> register signing keys for all topics to which they intend to send notifications. Tokens
     * may be registered at any time in a client's life-cycle.</p>
     *
     * @param signingKey the private key with which to sign authentication tokens
     * @param teamId the Apple-issued, ten-character identifier for the team to which the given private key belongs
     * @param keyId the Apple-issued, ten-character identifier for the given private key
     * @param topics the topics to which the given signing key is applicable
     *
     * @throws InvalidKeyException if the given key is invalid for any reason
     * @throws NoSuchAlgorithmException if the JRE does not support the required token-signing algorithm
     *
     * @since 0.9
     */
    public void registerKey(final ECPrivateKey signingKey, final String teamId, final String keyId, final Collection<String> topics) throws InvalidKeyException, NoSuchAlgorithmException {
        this.registerKey(signingKey, teamId, keyId, topics.toArray(new String[0]));
    }

    /**
     * <p>Registers a private signing key for the given topics. Clears any topics and keys previously associated with
     * the given team.</p>
     *
     * <p>Callers <em>must</em> register signing keys for all topics to which they intend to send notifications. Tokens
     * may be registered at any time in a client's life-cycle.</p>
     *
     * @param privateKey the private key with which to sign authentication tokens
     * @param teamId the Apple-issued, ten-character identifier for the team to which the given private key belongs
     * @param keyId the Apple-issued, ten-character identifier for the given private key
     * @param topics the topics to which the given signing key is applicable
     *
     * @throws InvalidKeyException if the given key is invalid for any reason
     * @throws NoSuchAlgorithmException if the JRE does not support the required token-signing algorithm
     *
     * @since 0.9
     */
    public void registerKey(final ECPrivateKey privateKey, final String teamId, final String keyId, final String... topics) throws InvalidKeyException, NoSuchAlgorithmException {
        this.registerKey(new ApnsSigningKey(keyId, teamId, privateKey), topics);
    }

    @Override
    protected ApnsSigningKey loadKey(final InputStream keyInputStream, final String teamId, final String keyId) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        final ECPrivateKey signingKey;
        {
            final String base64EncodedPrivateKey;
            {
                final StringBuilder privateKeyBuilder = new StringBuilder();

                final BufferedReader reader = new BufferedReader(new InputStreamReader(keyInputStream));
                boolean haveReadHeader = false;
                boolean haveReadFooter = false;

                for (String line; (line = reader.readLine()) != null; ) {
                    if (!haveReadHeader) {
                        if (line.contains("BEGIN PRIVATE KEY")) {
                            haveReadHeader = true;
                            continue;
                        }
                    } else {
                        if (line.contains("END PRIVATE KEY")) {
                            haveReadFooter = true;
                            break;
                        } else {
                            privateKeyBuilder.append(line);
                        }
                    }
                }

                if (!(haveReadHeader && haveReadFooter)) {
                    throw new IOException("Could not find private key header/footer");
                }

                base64EncodedPrivateKey = privateKeyBuilder.toString();
            }

            final ByteBuf wrappedEncodedPrivateKey = Unpooled.wrappedBuffer(base64EncodedPrivateKey.getBytes(StandardCharsets.US_ASCII));

            try {
                final ByteBuf decodedPrivateKey = Base64.decode(wrappedEncodedPrivateKey);

                try {
                    final byte[] keyBytes = new byte[decodedPrivateKey.readableBytes()];
                    decodedPrivateKey.readBytes(keyBytes);

                    final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
                    final KeyFactory keyFactory = KeyFactory.getInstance("EC");
                    signingKey = (ECPrivateKey) keyFactory.generatePrivate(keySpec);
                } catch (final InvalidKeySpecException e) {
                    throw new InvalidKeyException(e);
                } finally {
                    decodedPrivateKey.release();
                }
            } finally {
                wrappedEncodedPrivateKey.release();
            }
        }

        return new ApnsSigningKey(keyId, teamId, signingKey);
    }
}
