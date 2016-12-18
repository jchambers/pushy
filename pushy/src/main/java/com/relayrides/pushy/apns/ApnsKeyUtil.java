package com.relayrides.pushy.apns;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;

class ApnsKeyUtil {
    public static ECPrivateKey loadPrivateKey(final InputStream inputStream) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        final ECPrivateKey privateKey;
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
                    privateKey = (ECPrivateKey) keyFactory.generatePrivate(keySpec);
                } catch (final InvalidKeySpecException e) {
                    throw new InvalidKeyException(e);
                } finally {
                    decodedPrivateKey.release();
                }
            } finally {
                wrappedEncodedPrivateKey.release();
            }
        }

        return privateKey;
    }

    public static ECPublicKey loadPublicKey(final InputStream inputStream) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        final ECPublicKey publicKey;
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

        return publicKey;
    }
}
