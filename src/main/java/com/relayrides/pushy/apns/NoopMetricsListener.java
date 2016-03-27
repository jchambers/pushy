package com.relayrides.pushy.apns;

/**
 * A do-nothing metrics listener.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
class NoopMetricsListener implements ApnsClientMetricsListener {

    @Override
    public void handleWriteFailure(final ApnsClient<? extends ApnsPushNotification> apnsClient, final long notificationId) {
    }

    @Override
    public void handleNotificationSent(final ApnsClient<? extends ApnsPushNotification> apnsClient, final long notificationId) {
    }

    @Override
    public void handleNotificationAccepted(final ApnsClient<? extends ApnsPushNotification> apnsClient, final long notificationId) {
    }

    @Override
    public void handleNotificationRejected(final ApnsClient<? extends ApnsPushNotification> apnsClient, final long notificationId) {
    }

    @Override
    public void handleConnectionAttemptStarted(final ApnsClient<? extends ApnsPushNotification> apnsClient) {
    }

    @Override
    public void handleConnectionAttemptSucceeded(final ApnsClient<? extends ApnsPushNotification> apnsClient) {
    }

    @Override
    public void handleConnectionAttemptFailed(final ApnsClient<? extends ApnsPushNotification> apnsClient) {
    }
}
