/*
 * Copyright (c) 2020 Jon Chambers
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

package com.eatthepath.pushy.apns.auth;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ApnsSigningKeyTest extends ApnsKeyTest {

    @Override
    protected ApnsKey getApnsKey() throws NoSuchAlgorithmException, InvalidKeyException {
        return new ApnsSigningKey("Key ID", "Team ID",
            (ECPrivateKey) KeyPairGenerator.getInstance("EC").generateKeyPair().getPrivate());
    }

    @Test
    void loadFromInputStream() throws IOException, NoSuchAlgorithmException {
        final ECPrivateKey privateKey =
            (ECPrivateKey) KeyPairGenerator.getInstance("EC").generateKeyPair().getPrivate();

        final String pkcs8EncodedKey = "-----BEGIN PRIVATE KEY-----\r\n" +
            Base64.getMimeEncoder().encodeToString(privateKey.getEncoded()) +
            "\r\n-----END PRIVATE KEY-----\r\n";

        try (final ByteArrayInputStream keyInputStream = new ByteArrayInputStream(pkcs8EncodedKey.getBytes(StandardCharsets.UTF_8))) {
            assertDoesNotThrow(() -> ApnsSigningKey.loadFromInputStream(keyInputStream, "Team ID", "Key ID"));
        }
    }
}
