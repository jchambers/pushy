package com.eatthepath.pushy.apns;

/**
 * An exception thrown to indicate that a push notification should be rejected by the client
 * to avoid a large number of pending acquisitions.
 */
public class RejectedAcquisitionException extends Exception {

    public RejectedAcquisitionException(int maxPendingAcquisition) {
        super("The number of pending acquisitions has reached the upper limit [" + maxPendingAcquisition + "]");
    }
}
