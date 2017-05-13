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

import com.turo.pushy.apns.ApnsClient;
import org.apache.commons.codec.binary.Base64;

import java.io.*;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

/**
 * <p>A public key used to verify authentication tokens. Signing keys are associated with a developer team (in Apple's
 * parlance), and can be used to sign authentication tokens for any topic associated with that team.</p>
 *
 * <p>Callers generally won't need to use this class outside of the context of integration tests. In almost all cases,
 * callers will want to use an {@link ApnsSigningKey} to provide signing credentials to an
 * {@link ApnsClient}.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.10
 */
public class ApnsVerificationKey extends ApnsKey implements ECPublicKey {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new verification key with the given key identifier, team identifier, and elliptic curve private key.
     *
     * @param keyId the ten-character, Apple-issued identifier for the key itself
     * @param teamId the ten-character, Apple-issued identifier for the team to which the key belongs
     * @param key the elliptic curve private key underpinning this signing key
     *
     * @throws NoSuchAlgorithmException if the {@value APNS_SIGNATURE_ALGORITHM} algorith is not supported by the JVM
     * @throws InvalidKeyException if the given elliptic curve private key is invalid for any reason
     */
    public ApnsVerificationKey(final String keyId, final String teamId, final ECPublicKey key) throws NoSuchAlgorithmException, InvalidKeyException {
        super(keyId, teamId, key);

        // This is a little goofy, but we want to check early for missing algorithms or bogus keys, and the most direct
        // way to do that is to try to actually use the key to create a signature.
        final Signature signature = Signature.getInstance(ApnsKey.APNS_SIGNATURE_ALGORITHM);
        signature.initVerify(key);
    }

    @Override
    public ECPublicKey getKey() {
        return (ECPublicKey) super.getKey();
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
    public ECPoint getW() {
        return this.getKey().getW();
    }

    /**
     * Loads a verification key from the given PKCS#8 file.
     *
     * @param pkcs8File the file from which to load the key
     * @param teamId the ten-character, Apple-issued identifier for the team to which the key belongs
     * @param keyId the ten-character, Apple-issued identitifier for the key itself
     *
     * @return an APNs verification key with the given key ID and associated with the given team
     *
     * @throws IOException if a key could not be loaded from the given file for any reason
     * @throws NoSuchAlgorithmException if the JVM does not support elliptic curve keys
     * @throws InvalidKeyException if the loaded key is invalid for any reason
     */
    public static ApnsVerificationKey loadFromPkcs8File(final File pkcs8File, final String teamId, final String keyId) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        try (final FileInputStream fileInputStream = new FileInputStream(pkcs8File)) {
            return ApnsVerificationKey.loadFromInputStream(fileInputStream, teamId, keyId);
        }
    }

    /**
     * Loads a verification key from the given input stream.
     *
     * @param inputStream the input stream from which to load the key
     * @param teamId the ten-character, Apple-issued identifier for the team to which the key belongs
     * @param keyId the ten-character, Apple-issued identitifier for the key itself
     *
     * @return an APNs verification key with the given key ID and associated with the given team
     *
     * @throws IOException if a key could not be loaded from the given file for any reason
     * @throws NoSuchAlgorithmException if the JVM does not support elliptic curve keys
     * @throws InvalidKeyException if the loaded key is invalid for any reason
     */
    public static ApnsVerificationKey loadFromInputStream(final InputStream inputStream, final String teamId, final String keyId) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        final ECPublicKey verificationKey;
        {
            final String base64EncodedPublicKey;
            {
                final StringBuilder publicKeyBuilder = new StringBuilder();

                final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                boolean haveReadHeader = false;
                boolean haveReadFooter = false;

                for (String line; (line = reader.readLine()) != null; ) {
                    if (!haveReadHeader) {
                        if (line.contains("BEGIN PUBLIC KEY")) {
                            haveReadHeader = true;
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

            final byte[] keyBytes = Base64.decodeBase64(base64EncodedPublicKey);

            final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            final KeyFactory keyFactory = KeyFactory.getInstance("EC");

            try {
                verificationKey = (ECPublicKey) keyFactory.generatePublic(keySpec);
            } catch (InvalidKeySpecException e) {
                throw new InvalidKeyException(e);
            }
        }

        return new ApnsVerificationKey(keyId, teamId, verificationKey);
    }
}
