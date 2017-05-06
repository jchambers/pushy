package com.turo.pushy.apns.auth;

import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertNotNull;

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
}
