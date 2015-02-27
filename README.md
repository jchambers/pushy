# pushy

Pushy is a Java library for sending [APNs](http://developer.apple.com/library/mac/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Introduction.html) (iOS and OS X) push notifications. It is written and maintained by the engineers at [RelayRides](https://relayrides.com/) and is built on the [Netty framework](http://netty.io/).

Pushy was created because we found that the other APNs libraries for Java simply didn't meet our needs in terms of performance or (especially) reliability. Pushy distinguishes itself from other libraries with several important features:

- Asynchronous network IO (via Netty) for [maximum performance](https://github.com/relayrides/pushy/wiki/Performance)
- Efficient connection management
- Graceful handling and reporting of permanent notification rejections and connection failures
- Thorough [documentation](http://relayrides.github.io/pushy/apidocs/0.4/)

We believe that Pushy is already the best tool for sending APNs push notifications from Java applications, and we hope you'll help us make it even better via bug reports and pull requests. If you have questions about using Pushy, please join us on [the Pushy mailing list](https://groups.google.com/d/forum/pushy-apns) or take a look at [the wiki](https://github.com/relayrides/pushy/wiki). Thanks!

## Getting Pushy

If you use [Maven](http://maven.apache.org/), you can add Pushy to your project by adding the following dependency declaration to your POM:

```xml
<dependency>
    <groupId>com.relayrides</groupId>
    <artifactId>pushy</artifactId>
    <version>0.4.2</version>
</dependency>
```

If you don't use Maven, you can [download Pushy as a `.jar` file](https://github.com/relayrides/pushy/releases/download/pushy-0.4/pushy-0.4.jar) and add it to your project directly. You'll also need to make sure you have Pushy's runtime dependencies on your classpath. They are:

- [netty 4.0.24.Final](http://netty.io/)
- [slf4j 1.7.6](http://www.slf4j.org/)
- [json.simple 1.1.1](https://code.google.com/p/json-simple/)

Pushy itself requires Java 1.6 or newer.

## Using Pushy

The main public-facing part of Pushy is the [`PushManager`](http://relayrides.github.io/pushy/apidocs/0.4/com/relayrides/pushy/apns/PushManager.html) class, which manages connections to APNs and manages the queue of outbound notifications. Before you can create a `PushManager`, though, you'll need appropriate SSL certificates and keys from Apple. They can be obtained by following the steps in Apple's ["Provisioning and Development"](http://developer.apple.com/library/mac/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ProvisioningDevelopment.html#//apple_ref/doc/uid/TP40008194-CH104-SW1) guide.

Once you have your certificates and keys, you can construct a new `PushManager` like this:

```java
final PushManager<SimpleApnsPushNotification> pushManager =
    new PushManager<SimpleApnsPushNotification>(
        ApnsEnvironment.getSandboxEnvironment(),
        SSLContextUtil.createDefaultSSLContext("path-to-key.p12", "my-password"),
        null, // Optional: custom event loop group
        null, // Optional: custom ExecutorService for calling listeners
        null, // Optional: custom BlockingQueue implementation
        new PushManagerConfiguration(),
        "ExamplePushManager");

pushManager.start();
```

Many aspects of a `PushManager`'s behavior can be customized. See the [`PushManager` documentation](http://relayrides.github.io/pushy/apidocs/0.4/com/relayrides/pushy/apns/PushManager.html) for details. Some important highlights:

1. If you have multiple `PushManager` instances, you may want to share an event loop group and/or listener executor service between them to keep the number of active threads at a reasonable level.
2. By default, a `PushManager` will use an unbounded public queue; depending on your use case, you may want to use a bounded [`BlockingQueue`](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/BlockingQueue.html) implementation or even a [`SynchronousQueue`](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/SynchronousQueue.html).
3. Many other low-level options can be configured by providing a custom [`PushManagerConfiguration`](http://relayrides.github.io/pushy/apidocs/0.4/com/relayrides/pushy/apns/PushManagerConfiguration.html).

Once you have your `PushManager` constructed and started, you're ready to start constructing and sending push notifications. Pushy provides utility classes for working with APNs tokens and payloads. Here's an example:

```java
final byte[] token = TokenUtil.tokenStringToByteArray(
    "<5f6aa01d 8e335894 9b7c25d4 61bb78ad 740f4707 462c7eaf bebcf74f a5ddb387>");

final ApnsPayloadBuilder payloadBuilder = new ApnsPayloadBuilder();

payloadBuilder.setAlertBody("Ring ring, Neo.");
payloadBuilder.setSoundFileName("ring-ring.aiff");

final String payload = payloadBuilder.buildWithDefaultMaximumLength();

pushManager.getQueue().put(new SimpleApnsPushNotification(token, payload));
```

When your application shuts down, make sure to shut down the `PushManager`, too:

```java
pushManager.shutdown();
```

When the `PushManager` takes a notification from the queue, it will keep trying to send that notification. By the time you shut down the `PushManager` (as long as you don't give the shutdown process a timeout), the notification is guaranteed to have either been accepted or rejected by the APNs gateway.

## Error handling

Pushy deals with most problems for you, but there are two classes of problems you may want to deal with on your own.

### Rejected notifications

Push notification providers communicate with APNs by opening a long-lived connection to Apple's push notification gateway and streaming push notification through that connection. Apple's gateway won't respond or acknowledge push notifications unless something goes wrong, in which case it will send an error code and close the connection (don't worry -- Pushy deals with all of this for you). To deal with notifications that are rejected by APNs, Pushy provides a notion of a [`RejectedNotificationListener`](http://relayrides.github.io/pushy/apidocs/0.4/com/relayrides/pushy/apns/RejectedNotificationListener.html). Rejected notification listeners are informed whenever APNs rejects a push notification. Here's an example of registering a simple listener:

```java
private class MyRejectedNotificationListener implements RejectedNotificationListener<SimpleApnsPushNotification> {

    @Override
    public void handleRejectedNotification(
            final PushManager<? extends SimpleApnsPushNotification> pushManager,
            final SimpleApnsPushNotification notification,
            final RejectedNotificationReason reason) {

        System.out.format("%s was rejected with rejection reason %s\n", notification, reason);
    }
}

// ...

pushManager.registerRejectedNotificationListener(new MyRejectedNotificationListener());
```

Lots of things can go wrong when sending notifications, but rejected notification listeners are only informed when Apple definitively rejects a push notification. All other IO problems are treated as temporary issues, and Pushy will automatically re-transmit notifications affected by IO problems later. You may register a rejected notification listener at any time before shutting down the `PushManager`.

### Failed connections

While running, a `PushManager` will attempt to re-open any connection that is closed by the gateway (i.e. if a notification was rejected). Occasionally, connection attempts will fail for benign (or at least temporary) reasons. Sometimes, though, connection failures can indicate a more permanent problem (like an expired certificate) that won't be resolved by retrying the connection, and letting the `PushManager` try to reconnect indefinitely won't help the situation.

You can listen for connection failures with a [`FailedConnectionListener`](http://relayrides.github.io/pushy/apidocs/0.4/com/relayrides/pushy/apns/FailedConnectionListener.html) like this:

```java
private class MyFailedConnectionListener implements FailedConnectionListener<SimpleApnsPushNotification> {

    @Override
    public void handleFailedConnection(
            final PushManager<? extends SimpleApnsPushNotification> pushManager,
            final Throwable cause) {

        if (cause instanceof SSLHandshakeException) {
            // This is probably a permanent failure, and we should shut down
            // the PushManager.
        }
    }
}

// ...

pushManager.registerFailedConnectionListener(new MyFailedConnectionListener());
```

Generally, it's safe to ignore most failures (though you may want to log them). Failures that result from a `SSLHandshakeException`, though, likely indicate that your certificate is either invalid or expired, and you'll need to remedy the situation before reconnection attempts are likely to succeed. Poorly-timed connection issues may cause spurious exceptions, though, and it's wise to look for a pattern of failures before taking action.

Like `RejectedNotificationListeners`, `FailedConnectionListeners` can be registered any time before the `PushManager` is shut down.

## The feedback service

Apple also provides a "feedback service" as part of APNs. The feedback service reports which tokens are no longer valid because the end user uninstalled the receiving app. Apple requires push notification providers to poll for expired tokens on a daily basis. See ["The Feedback Service"](http://developer.apple.com/library/mac/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/CommunicatingWIthAPS.html#//apple_ref/doc/uid/TP40008194-CH101-SW3) for additional details from Apple.

To get expired device tokens with Pushy, you'll need to register an [`ExpiredTokenListener`](http://relayrides.github.io/pushy/apidocs/0.4/com/relayrides/pushy/apns/ExpiredTokenListener.html) with your `PushManager`, then call the `requestExpiredTokens` method. For example:

```java
private class MyExpiredTokenListener implements ExpiredTokenListener<SimpleApnsPushNotification> {

    @Override
    public void handleExpiredTokens(
            final PushManager<? extends SimpleApnsPushNotification> pushManager,
            final Collection<ExpiredToken> expiredTokens) {

        for (final ExpiredToken expiredToken : expiredTokens) {
            // Stop sending push notifications to each expired token if the expiration
            // time is after the last time the app registered that token.
        }
    }
}

// ...

pushManager.registerExpiredTokenListener(new MyExpiredTokenListener());
pushManager.requestExpiredTokens();
```

## Logging

Pushy uses [SLF4J](http://www.slf4j.org/) for logging. If you're not already familiar with it, SLF4J is a facade that allows users to choose which logging library to use at deploy time by adding a specific "binding" to the classpath. To avoid making the choice for you, Pushy itself does *not* depend on any SLF4J bindings; you'll need to add one on your own (either by adding it as a dependency in your own project or by installing it directly). If you have no SLF4J bindings on your classpath, you'll probably see a warning that looks something like this:

```
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
SLF4J: Defaulting to no-operation (NOP) logger implementation
SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.
```

For more information, see the [SLF4J user manual](http://www.slf4j.org/manual.html).

Pushy uses logging levels as follows:

| Level     | Events logged                                                                         |
|-----------|---------------------------------------------------------------------------------------|
| `error`   | Serious, unrecoverable errors; recoverable errors that likely indicate a bug in Pushy |
| `warn`    | Serious, but recoverable errors; errors that may indicate a bug in caller's code      |
| `info`    | Important lifecycle events                                                            |
| `debug`   | Minor lifecycle events; expected exceptions                                           |
| `trace`   | Individual IO operations                                                              |

## Limitations and known issues

Although we make every effort to fix bugs and work around issues outside of our control, some problems appear to be unavoidable. The issues we know about at this time are:

- In cases where we successfully write a push notification to the OS-controlled outbound buffer, but the notification has not yet been written to the network, the push notification will be silently lost if the TCP connection is closed before the OS sends the notification over the network. See [#14](https://github.com/relayrides/pushy/issues/14) for additional discussion.
- Under Windows, if writing a notification fails after the APNs gateway has rejected a notification and closed the connection remotely, the rejection details may be lost. See [#6](https://github.com/relayrides/pushy/issues/14) for additional discussion.
- We recommend against using Pushy in a container environment (e.g. a servlet container like Tomcat). Netty, by design, leaves behind some long-running threads and `ThreadLocal` instances that we can't reliably clean up, and this can cause leaks when shutting down your app. To be clear, Pushy will send notifications as expected from a container environment, but may not be cleaned up properly at shutdown. If you choose to use Pushy in a container environment, please be aware of the following issues:
  - After shutting down Pushy, a `GlobalEventExecutor` owned by Netty will continue running for about a second. This can cause warnings in environments that look for thread/resource leaks (e.g. servlet containers), but there is no real harm because the `GlobalEventExecutor` will eventually shut itself down. To avoid warnings in these environments, you can add a `Thread.sleep(1000)` call after shutting down Pushy. See [#29](https://github.com/relayrides/pushy/issues/29) for additional discussion.
  - A number of `ThreadLocal` instances will be left behind by Netty, and these are likely to cause memory leaks. See [#73](https://github.com/relayrides/pushy/issues/73) for details and additional discussion.

## License and status

Pushy is available to the public under the [MIT License](http://opensource.org/licenses/MIT).

The current version of Pushy is 0.4.2. We consider it to be fully functional (and use it in production!), but the public API may change significantly before a 1.0 release.
