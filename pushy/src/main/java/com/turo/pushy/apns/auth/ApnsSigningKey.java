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

import org.apache.commons.codec.binary.Base64;

import java.io.*;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * A private key used to sign authentication tokens. Signing keys are associated with a developer team (in Apple's
 * parlance), and can be used to sign authentication tokens for any topic associated with that team.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.10
 */
public class ApnsSigningKey extends ApnsKey implements ECPrivateKey {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new signing key with the given key identifier, team identifier, and elliptic curve private key.
     *
     * @param keyId the ten-character, Apple-issued identifier for the key itself
     * @param teamId the ten-character, Apple-issued identifier for the team to which the key belongs
     * @param key the elliptic curve public key underpinning this verification key
     *
     * @throws NoSuchAlgorithmException if the {@value APNS_SIGNATURE_ALGORITHM} algorith is not supported by the JVM
     * @throws InvalidKeyException if the given elliptic curve private key is invalid for any reason
     */
    public ApnsSigningKey(final String keyId, final String teamId, final ECPrivateKey key) throws NoSuchAlgorithmException, InvalidKeyException {
        super(keyId, teamId, key);

        // This is a little goofy, but we want to check early for missing algorithms or bogus keys, and the most direct
        // way to do that is to try to actually use the key to create a signature.
        final Signature signature = Signature.getInstance(ApnsKey.APNS_SIGNATURE_ALGORITHM);
        signature.initSign(key);
    }

    @Override
    protected ECPrivateKey getKey() {
        return (ECPrivateKey) super.getKey();
    }

    @Override
    public String getAlgorithm() {
        return this.getKey().getAlgorithm();
    }

    @Override
    public String getFormat() {
        return this.getKey().getFormat();
    }

    @Override
    public byte[] getEncoded() {
        return this.getKey().getEncoded();
    }

    @Override
    public BigInteger getS() {
        return this.getKey().getS();
    }

    /**
     * Loads a signing key from the given PKCS#8 file.
     *
     * @param pkcs8File the file from which to load the key
     * @param teamId the ten-character, Apple-issued identifier for the team to which the key belongs
     * @param keyId the ten-character, Apple-issued identitifier for the key itself
     *
     * @return an APNs signing key with the given key ID and associated with the given team
     *
     * @throws IOException if a key could not be loaded from the given file for any reason
     * @throws NoSuchAlgorithmException if the JVM does not support elliptic curve keys
     * @throws InvalidKeyException if the loaded key is invalid for any reason
     */
    public static ApnsSigningKey loadFromPkcs8File(final File pkcs8File, final String teamId, final String keyId) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        try (final FileInputStream fileInputStream = new FileInputStream(pkcs8File)) {
            return ApnsSigningKey.loadFromInputStream(fileInputStream, teamId, keyId);
        }
    }

    /**
     * Loads a signing key from the given input stream.
     *
     * @param inputStream the input stream from which to load the key
     * @param teamId the ten-character, Apple-issued identifier for the team to which the key belongs
     * @param keyId the ten-character, Apple-issued identitifier for the key itself
     *
     * @return an APNs signing key with the given key ID and associated with the given team
     *
     * @throws IOException if a key could not be loaded from the given file for any reason
     * @throws NoSuchAlgorithmException if the JVM does not support elliptic curve keys
     * @throws InvalidKeyException if the loaded key is invalid for any reason
     */
    public static ApnsSigningKey loadFromInputStream(final InputStream inputStream, final String teamId, final String keyId) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        final ECPrivateKey signingKey;
        {
            final String base64EncodedPrivateKey;
            {
                final StringBuilder privateKeyBuilder = new StringBuilder();

                final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                boolean haveReadHeader = false;
                boolean haveReadFooter = false;

                for (String line; (line = reader.readLine()) != null; ) {
                    if (!haveReadHeader) {
                        if (line.contains("BEGIN PRIVATE KEY")) {
                            haveReadHeader = true;
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

            final byte[] keyBytes = Base64.decodeBase64(base64EncodedPrivateKey);

            final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            final KeyFactory keyFactory = KeyFactory.getInstance("EC");

            try {
                signingKey = (ECPrivateKey) keyFactory.generatePrivate(keySpec);
            } catch (InvalidKeySpecException e) {
                throw new InvalidKeyException(e);
            }
        }

        return new ApnsSigningKey(keyId, teamId, signingKey);
    }
}
