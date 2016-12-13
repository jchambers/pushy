package com.relayrides.pushy.apns;

/**
 * Created by dime on 22/08/16.
 */
public interface ApnsClientDisconnectListener {
    /**
     * Called when the client unexpectedly disconnects
     */
    void onDisconnect();
}
