package com.relayrides.pushy.apns.auth;

import org.apache.commons.codec.binary.Base64;

import java.io.*;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

public class ApnsVerificationKey extends ApnsKey implements ECPublicKey {

    private static final long serialVersionUID = 1L;

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

    public static ApnsVerificationKey loadFromPkcs8File(final File pkcs8File, final String teamId, final String keyId) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        try (final FileInputStream fileInputStream = new FileInputStream(pkcs8File)) {
            return ApnsVerificationKey.loadFromInputStream(fileInputStream, teamId, keyId);
        }
    }

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

            final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
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
