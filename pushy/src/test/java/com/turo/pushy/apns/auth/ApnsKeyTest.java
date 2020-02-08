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

package com.turo.pushy.apns.auth;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(JUnitParamsRunner.class)
public abstract class ApnsKeyTest {

    protected abstract ApnsKey getApnsKey() throws NoSuchAlgorithmException, InvalidKeyException, IOException;

    @Test
    public void testGetKeyId() throws Exception {
        assertNotNull(this.getApnsKey().getKeyId());
    }

    @Test
    public void testGetTeamId() throws Exception {
        assertNotNull(this.getApnsKey().getTeamId());
    }

    @Test
    public void testGetKey() throws Exception {
        assertNotNull(this.getApnsKey().getKey());
    }

    @Test
    public void testGetParams() throws Exception {
        assertNotNull(this.getApnsKey().getParams());
    }

    @Test
    // Test vectors from https://tools.ietf.org/html/rfc4648#section-10
    @Parameters({
            "Zg==,     f",
            "Zm8=,     fo",
            "Zm9v,     foo",
            "Zm9vYg==, foob",
            "Zm9vYmE=, fooba",
            "Zm9vYmFy, foobar"  })
    public void testDecodeBase64EncodedString(final String base64EncodedString, final String decodedAsciiString) {
        assertEquals(decodedAsciiString, new String(ApnsKey.decodeBase64EncodedString(base64EncodedString), StandardCharsets.US_ASCII));
    }
}
