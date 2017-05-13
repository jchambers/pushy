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

package com.turo.pushy.apns;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.security.KeyStoreException;
import java.security.KeyStore.PrivateKeyEntry;

import org.junit.Test;

public class P12UtilTest {

    private static final String SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME = "/single-topic-client.p12";
    private static final String MULTIPLE_KEY_KEYSTORE_FILENAME = "/multiple-keys.p12";
    private static final String NO_KEY_KEYSTORE_FILENAME = "/no-keys.p12";

    private static final String KEYSTORE_PASSWORD = "pushy-test";

    @Test
    public void testGetPrivateKeyEntryFromP12InputStream() throws Exception {
        try (final InputStream p12InputStream = P12UtilTest.class.getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME)) {
            final PrivateKeyEntry privateKeyEntry =
                    P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            assertNotNull(privateKeyEntry);
        }
    }

    @Test
    public void testGetPrivateKeyEntryFromP12InputStreamWithMultipleKeys() throws Exception {
        try (final InputStream p12InputStream = P12UtilTest.class.getResourceAsStream(MULTIPLE_KEY_KEYSTORE_FILENAME)) {
            final PrivateKeyEntry privateKeyEntry =
                    P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            assertNotNull(privateKeyEntry);
        }
    }

    @Test(expected = KeyStoreException.class)
    public void testGetPrivateKeyEntryFromP12InputStreamWithNoKeys() throws Exception {
        try (final InputStream p12InputStream = P12UtilTest.class.getResourceAsStream(NO_KEY_KEYSTORE_FILENAME)) {
            P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);
        }
    }
}
