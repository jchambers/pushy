package com.relayrides.pushy.apns;

import org.junit.Test;

public class ExpiredTokenTest {

    @Test(expected = NullPointerException.class)
    public void ConstructorCantHandleNullToken() {
        new ExpiredToken(null, new java.util.Date());
    }
}
