package com.relayrides.pushy.apns;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.base64.Base64Dialect;

class AuthenticationTokenSupplier {

    private final Signature signature;

    private final String issuer;
    private final String keyId;

    private String token;

    private static final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(Date.class, new DateAsTimeSinceEpochTypeAdapter(TimeUnit.SECONDS))
            .create();

    public AuthenticationTokenSupplier(final String issuer, final String keyId, final PrivateKey privateKey) throws NoSuchAlgorithmException, InvalidKeyException {
        Objects.requireNonNull(issuer);
        Objects.requireNonNull(keyId);
        Objects.requireNonNull(privateKey);

        this.signature = Signature.getInstance("SHA256withECDSA");
        this.signature.initSign(privateKey);

        this.issuer = issuer;
        this.keyId = keyId;
    }

    public String getToken() throws SignatureException {
        return this.getToken(new Date());
    }

    protected String getToken(final Date issuedAt) throws SignatureException {
        if (this.token == null) {
            final String header = gson.toJson(new AuthenticationTokenHeader(this.keyId));
            final String claims = gson.toJson(new AuthenticationTokenClaims(this.issuer, issuedAt));

            final String payloadWithoutSignature = String.format("%s.%s",
                    base64UrlEncodeWithoutPadding(header.getBytes(StandardCharsets.US_ASCII)),
                    base64UrlEncodeWithoutPadding(claims.getBytes(StandardCharsets.US_ASCII)));

            final byte[] signatureBytes;
            {
                this.signature.update(payloadWithoutSignature.getBytes(StandardCharsets.US_ASCII));
                signatureBytes = this.signature.sign();
            }

            this.token = String.format("%s.%s", payloadWithoutSignature,
                    base64UrlEncodeWithoutPadding(signatureBytes));
        }

        return this.token;
    }

    public void invalidateToken(final String invalidToken) {
        if (invalidToken != null && invalidToken.equals(this.token)) {
            this.token = null;
        }
    }

    private static String base64UrlEncodeWithoutPadding(final byte[] bytes) {
        final ByteBuf wrappedString = Unpooled.wrappedBuffer(bytes);
        final ByteBuf encodedString = Base64.encode(wrappedString, Base64Dialect.URL_SAFE);

        final String encodedUnpaddedString = encodedString.toString(StandardCharsets.US_ASCII).replace("=", "");

        wrappedString.release();
        encodedString.release();

        return encodedUnpaddedString;
    }
}
