package com.relayrides.pushy.apns.util;

import javax.net.ssl.SSLException;

/**
 * Created by nsun on 15-6-1.
 */
public class ExceptionUtil {

    /**
     * Test if an exception is a fatal error, which reconnection doesn't solve the issue.
     *
     * Exceptions currently considered as FATAL:
     * <ul>
     *     <li>javax.net.ssl.SSLException: Received fatal alert: certificate_revoked</li>
     *     <li>javax.net.ssl.SSLException: Received fatal alert: certificate_expired</li>
     * </ul>
     *
     * @param e
     * @return
     */
    public static boolean isFatal(Throwable e) {
        if (e instanceof SSLException) {
            return e.getMessage().contains("Received fatal alert");
        }
        return false;
    }
}
