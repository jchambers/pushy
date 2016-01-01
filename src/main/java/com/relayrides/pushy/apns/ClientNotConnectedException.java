package com.relayrides.pushy.apns;

/**
 * An exception thrown to indicate that a notification could not be sent because the client was not connected.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public class ClientNotConnectedException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
}
