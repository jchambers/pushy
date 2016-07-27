package com.relayrides.pushy.apns;

/**
 * A do-nothing metrics listener.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
class NoopMetricsListener implements ApnsClientMetricsListener {

    @Override
    public void handleWriteFailure(final ApnsClient apnsClient, final long notificationId) {
    }

    @Override
    public void handleNotificationSent(final ApnsClient apnsClient, final long notificationId) {
    }

    @Override
    public void handleNotificationAccepted(final ApnsClient apnsClient, final long notificationId) {
    }

    @Override
    public void handleNotificationRejected(final ApnsClient apnsClient, final long notificationId) {
    }

    @Override
    public void handleConnectionAttemptStarted(final ApnsClient apnsClient) {
    }

    @Override
    public void handleConnectionAttemptSucceeded(final ApnsClient apnsClient) {
    }

    @Override
    public void handleConnectionAttemptFailed(final ApnsClient apnsClient) {
    }
}
