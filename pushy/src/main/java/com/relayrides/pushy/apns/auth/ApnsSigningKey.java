package com.relayrides.pushy.apns.auth;

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

public class ApnsSigningKey extends ApnsKey implements ECPrivateKey {

    private static final long serialVersionUID = 1L;

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

    public static ApnsSigningKey loadFromPkcs8File(final File pkcs8File, final String teamId, final String keyId) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        try (final FileInputStream fileInputStream = new FileInputStream(pkcs8File)) {
            return ApnsSigningKey.loadFromInputStream(fileInputStream, teamId, keyId);
        }
    }

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
