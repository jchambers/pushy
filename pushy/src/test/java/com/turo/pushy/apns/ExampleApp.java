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

package com.turo.pushy.apns;

import com.turo.pushy.apns.auth.ApnsSigningKey;
import com.turo.pushy.apns.proxy.Socks5ProxyHandlerFactory;
import com.turo.pushy.apns.util.ApnsPayloadBuilder;
import com.turo.pushy.apns.util.SimpleApnsPushNotification;
import com.turo.pushy.apns.util.TokenUtil;
import com.turo.pushy.apns.util.concurrent.PushNotificationFuture;
import com.turo.pushy.apns.util.concurrent.PushNotificationResponseListener;
import io.netty.util.concurrent.Future;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

/**
 * This is a simple app that goes through the motions of creating an {@link ApnsClient}, connecting to the APNs
 * gateway, and sending a push notification. There's no expectation that this will actually work (we're referencing
 * credentials that don't actually exist, and the token/topic are crazy), but it should demonstrate all of the important
 * steps.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public class ExampleApp {

    public static void main(final String[] args) throws Exception {
        // The first thing to do is to create an APNs client. Clients need a
        // certificate and private key OR a signing key to authenticate with
        // the APNs server.
        final ApnsClient apnsClient = new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
                .setSigningKey(ApnsSigningKey.loadFromPkcs8File(new File("/path/to/key.p8"),
                        "TEAMID1234", "KEYID67890"))
                .build();

        // Optional: we can listen for metrics by setting a metrics listener.
        final ApnsClient apnsClientWithMetricsListener = new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
                .setSigningKey(ApnsSigningKey.loadFromPkcs8File(new File("/path/to/key.p8"),
                        "TEAMID1234", "KEYID67890"))
                // .setMetricsListener(new NoopMetricsListener())
                .build();

        // Optional: we can set a proxy handler factory if we must use a proxy.
        final ApnsClient apnsClientWithProxyHandler = new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
                .setSigningKey(ApnsSigningKey.loadFromPkcs8File(new File("/path/to/key.p8"),
                        "TEAMID1234", "KEYID67890"))
                .setProxyHandlerFactory(new Socks5ProxyHandlerFactory(
                        new InetSocketAddress("my.proxy.com", 1080), "username", "password"))
                .build();

        // Clients create new connections on demand. Once we've created a
        // client, we can start sending push notifications.
        final SimpleApnsPushNotification pushNotification;

        {
            final ApnsPayloadBuilder payloadBuilder = new ApnsPayloadBuilder();
            payloadBuilder.setAlertBody("Example!");

            final String payload = payloadBuilder.buildWithDefaultMaximumLength();
            final String token = TokenUtil.sanitizeTokenString("<efc7492 bdbd8209>");

            pushNotification = new SimpleApnsPushNotification(token, "com.example.myApp", payload);
        }

        // Sending notifications is an asynchronous process. We'll get a Future
        // immediately, but will need to wait for the Future to complete before
        // we'll know whether the notification was accepted or rejected by the
        // APNs gateway.
        final PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> sendNotificationFuture =
                apnsClient.sendNotification(pushNotification);

        try {
            final PushNotificationResponse<SimpleApnsPushNotification> pushNotificationResponse =
                    sendNotificationFuture.get();

            if (pushNotificationResponse.isAccepted()) {
                // Everything worked! The notification was successfully sent to
                // and accepted by the gateway.
            } else {
                // Something went wrong; this should be considered a permanent
                // failure, and we shouldn't try to send the notification again.
                System.out.println("Notification rejected by the APNs gateway: " +
                        pushNotificationResponse.getRejectionReason());

                if (pushNotificationResponse.getTokenInvalidationTimestamp() != null) {
                    // If we have an invalidation timestamp, we should also stop
                    // trying to send notifications to the destination token (unless
                    // it's been renewed somehow since the expiration timestamp).
                }
            }
        } catch (final ExecutionException e) {
            // Something went wrong when trying to send the notification to the
            // APNs gateway. The notification never actually reached the gateway,
            // so we shouldn't consider this a permanent failure.
            System.err.println("Failed to send push notification.");
            e.printStackTrace();
        }

        // To send push notifications efficiently, it's best to attach listeners
        // to "send push notification" futures so we don't have to wait for the
        // server to reply before we start sending the next notification.
        sendNotificationFuture.addListener(new PushNotificationResponseListener<SimpleApnsPushNotification>() {
            @Override
            public void operationComplete(final PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> future) throws Exception {
                // When using a listener, callers should check for a failure to send a
                // notification by checking whether the future itself was successful
                // since an exception will not be thrown.
                if (future.isSuccess()) {
                    final PushNotificationResponse<SimpleApnsPushNotification> pushNotificationResponse =
                            sendNotificationFuture.getNow();

                    // Handle the push notification response as before from here.
                } else {
                    // Something went wrong when trying to send the notification to the
                    // APNs gateway. We can find the exception that caused the failure
                    // by getting future.cause().
                    future.cause().printStackTrace();
                }
            }
        });

        // Finally, when we're done sending notifications (i.e. when our
        // application is shutting down), we should close all APNs clients
        // that may be in play.
        final Future<Void> closeFuture = apnsClient.close();
        closeFuture.await();
    }
}
