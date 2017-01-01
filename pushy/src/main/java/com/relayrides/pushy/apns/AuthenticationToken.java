package com.relayrides.pushy.apns;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.base64.Base64Dialect;

public class AuthenticationToken {

    private static class AuthenticationTokenHeader {
        @SerializedName("alg")
        private final String algorithm = "ES256";

        @SerializedName("typ")
        private final String tokenType = "JWT";

        @SerializedName("kid")
        private final String keyId;

        public AuthenticationTokenHeader(final String keyId) {
            this.keyId = keyId;
        }

        @SuppressWarnings("unused")
        public String getAlgorithm() {
            return this.algorithm;
        }

        @SuppressWarnings("unused")
        public String getTokenType() {
            return this.tokenType;
        }

        public String getKeyId() {
            return this.keyId;
        }
    }

    private static class AuthenticationTokenClaims {

        @SerializedName("iss")
        private final String issuer;

        @SerializedName("iat")
        private final Date issuedAt;

        public AuthenticationTokenClaims(final String teamId, final Date issuedAt) {
            this.issuer = teamId;
            this.issuedAt = issuedAt;
        }

        public String getIssuer() {
            return this.issuer;
        }

        public Date getIssuedAt() {
            return this.issuedAt;
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(Date.class, new DateAsTimeSinceEpochTypeAdapter(TimeUnit.SECONDS))
            .create();

    private static final long AUTH_TOKEN_EXPIRATION_MILLIS = TimeUnit.HOURS.toMillis(1);

    private final AuthenticationTokenHeader header;
    private final AuthenticationTokenClaims claims;
    private final byte[] signatureBytes;

    private final String base64EncodedToken;

    public AuthenticationToken(final ApnsSigningKey signingKey, final Date issuedAt) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        this.header = new AuthenticationTokenHeader(signingKey.getKeyId());
        this.claims = new AuthenticationTokenClaims(signingKey.getTeamId(), issuedAt);

        final String headerJson = GSON.toJson(this.header);
        final String claimsJson = GSON.toJson(this.claims);

        final StringBuilder payloadBuilder = new StringBuilder();
        payloadBuilder.append(base64UrlEncodeWithoutPadding(headerJson.getBytes(StandardCharsets.US_ASCII)));
        payloadBuilder.append('.');
        payloadBuilder.append(base64UrlEncodeWithoutPadding(claimsJson.getBytes(StandardCharsets.US_ASCII)));

        {
            final Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(signingKey);
            signature.update(payloadBuilder.toString().getBytes(StandardCharsets.US_ASCII));

            this.signatureBytes = signature.sign();
        }

        payloadBuilder.append('.');
        payloadBuilder.append(base64UrlEncodeWithoutPadding(this.signatureBytes));

        this.base64EncodedToken = payloadBuilder.toString();
    }

    public AuthenticationToken(final String base64EncodedToken) {
        this.base64EncodedToken = base64EncodedToken;

        final String[] pieces = this.base64EncodedToken.split("\\.");

        if (pieces.length != 3) {
            throw new IllegalArgumentException();
        }

        this.header = GSON.fromJson(padAndBase64UrlDecodeAsciiString(pieces[0]), AuthenticationTokenHeader.class);
        this.claims = GSON.fromJson(padAndBase64UrlDecodeAsciiString(pieces[1]), AuthenticationTokenClaims.class);
        this.signatureBytes = padAndBase64UrlDecodeByteArray(pieces[2]);
    }

    public Date getExpiration() {
        return new Date(this.claims.getIssuedAt().getTime() + AUTH_TOKEN_EXPIRATION_MILLIS);
    }

    public boolean verifySignature(final ApnsVerificationKey verificationKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        if (!this.header.getKeyId().equals(verificationKey.getKeyId())) {
            return false;
        }

        if (!this.claims.getIssuer().equals(verificationKey.getTeamId())) {
            return false;
        }

        final byte[] headerAndClaimsBytes;

        final String headerJson = GSON.toJson(this.header);
        final String claimsJson = GSON.toJson(this.claims);

        final StringBuilder headerAndClaimsBuilder = new StringBuilder();
        headerAndClaimsBuilder.append(base64UrlEncodeWithoutPadding(headerJson.getBytes(StandardCharsets.US_ASCII)));
        headerAndClaimsBuilder.append('.');
        headerAndClaimsBuilder.append(base64UrlEncodeWithoutPadding(claimsJson.getBytes(StandardCharsets.US_ASCII)));

        headerAndClaimsBytes = headerAndClaimsBuilder.toString().getBytes(StandardCharsets.US_ASCII);

        final Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(verificationKey);
        signature.update(headerAndClaimsBytes);

        return signature.verify(this.signatureBytes);
    }

    private static String base64UrlEncodeWithoutPadding(final byte[] bytes) {
        final ByteBuf wrappedString = Unpooled.wrappedBuffer(bytes);
        final ByteBuf encodedString = Base64.encode(wrappedString, Base64Dialect.URL_SAFE);

        final String encodedUnpaddedString = encodedString.toString(StandardCharsets.US_ASCII).replace("=", "");

        wrappedString.release();
        encodedString.release();

        return encodedUnpaddedString;
    }

    private static String padAndBase64UrlDecodeAsciiString(final String source) {
        final ByteBuf decodedBuffer = padAndBase64UrlDecode(source);

        try {
            return decodedBuffer.toString(StandardCharsets.US_ASCII);
        } finally {
            decodedBuffer.release();
        }
    }

    private static byte[] padAndBase64UrlDecodeByteArray(final String source) {
        final ByteBuf decodedBuffer = padAndBase64UrlDecode(source);

        try {
            final byte[] decodedBytes = new byte[decodedBuffer.readableBytes()];
            decodedBuffer.readBytes(decodedBytes);

            return decodedBytes;
        } finally {
            decodedBuffer.release();
        }
    }

    private static ByteBuf padAndBase64UrlDecode(final String source) {
        final byte[] sourceBytes = source.getBytes(StandardCharsets.US_ASCII);

        final int paddedLength = sourceBytes.length + 2;
        final ByteBuf sourceBuffer = Unpooled.buffer(paddedLength);

        try {
            sourceBuffer.writeBytes(sourceBytes);

            switch (sourceBytes.length % 4) {
                case 2: {
                    sourceBuffer.writeByte('=');
                    sourceBuffer.writeByte('=');
                    break;
                }

                case 3: {
                    sourceBuffer.writeByte('=');
                    break;
                }
            }

            return Base64.decode(sourceBuffer, Base64Dialect.URL_SAFE);
        } finally {
            sourceBuffer.release();
        }
    }

    @Override
    public String toString() {
        return this.base64EncodedToken;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.base64EncodedToken == null) ? 0 : this.base64EncodedToken.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof AuthenticationToken)) {
            return false;
        }
        final AuthenticationToken other = (AuthenticationToken) obj;
        if (this.base64EncodedToken == null) {
            if (other.base64EncodedToken != null) {
                return false;
            }
        } else if (!this.base64EncodedToken.equals(other.base64EncodedToken)) {
            return false;
        }
        return true;
    }
}
