---
layout: home
---

Pushy is a Java library for sending [APNs](https://developer.apple.com/documentation/usernotifications) (iOS, macOS, and Safari) push notifications. It is written and maintained by the engineers at [Turo](https://turo.com/).

Pushy sends push notifications using Apple's HTTP/2-based APNs protocol and supports both TLS and token-based authentication. It distinguishes itself from other push notification libraries with a focus on [thorough documentation](http://relayrides.github.io/pushy/apidocs/0.13/), asynchronous operation, and design for industrial-scale operation; with Pushy, it's easy and efficient to maintain multiple parallel connections to the APNs gateway to send large numbers of notifications to many different applications ("topics").

We believe that Pushy is already the best tool for sending APNs push notifications from Java applications, and we hope you'll help us make it even better via bug reports and pull requests. If you have questions about using Pushy, please join us on [the Pushy mailing list](https://groups.google.com/d/forum/pushy-apns) or take a look at [the wiki](https://github.com/relayrides/pushy/wiki). Thanks!

## Getting Pushy

If you use [Maven](http://maven.apache.org/), you can add Pushy to your project by adding the following dependency declaration to your POM:

```xml
<dependency>
    <groupId>com.turo</groupId>
    <artifactId>pushy</artifactId>
    <version>0.13.6</version>
</dependency>
```

If you don't use Maven (or something else that understands Maven dependencies, like Gradle), you can [download Pushy as a `.jar` file](https://github.com/relayrides/pushy/releases/download/pushy-0.13.6/pushy-0.13.6.jar) and add it to your project directly. You'll also need to make sure you have Pushy's runtime dependencies on your classpath. They are:

- [netty 4.1.25](http://netty.io/)
- [gson 2.6](https://github.com/google/gson)
- [slf4j 1.7](http://www.slf4j.org/) (and possibly an SLF4J binding, as described in the [logging](#logging) section below)
- [fast-uuid 0.1](https://github.com/jchambers/fast-uuid)

Pushy itself requires Java 7 or newer to build and run. Under Java 7, Pushy has an additional dependency (included automatically by dependency management systems) on [netty-tcnative 2.0.8.Final](http://netty.io/wiki/forked-tomcat-native.html), a native SSL provider that (among other benefits) includes ciphers required by APNs that are not included with Java 7 by default.

Under Java 8 and newer, Pushy does not require a native SSL provider, but users may choose to use it regardless for enhanced performance. To use a native provider, make sure netty-tcnative is on your classpath. Maven users may add a dependency to their project as follows:

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-tcnative-boringssl-static</artifactId>
    <version>2.0.8.Final</version>
    <scope>runtime</scope>
</dependency>
```

## Authenticating with the APNs server

Before you can get started with Pushy, you'll need to do some provisioning work with Apple to register your app and get the required certificates or signing keys (more on these shortly). For details on this process, please see the [Registering Your App with APNs](https://developer.apple.com/documentation/usernotifications/registering_your_app_with_apns) section of Apple's UserNotifications documentation. Please note that there are [some caveats](https://github.com/relayrides/pushy/wiki/Certificates), particularly under macOS 10.13 (El Capitan).

Generally speaking, APNs clients must authenticate with the APNs server by some means before they can send push notifications. Currently, APNs (and Pushy) supports two authentication methods: TLS-based authentication and token-based authentication. The two approaches are mutually-exclusive; you'll need to pick one or the other for each client.

### TLS authentication

In TLS-based authentication, clients present a TLS certificate to the server when connecting, and may send notifications to any "topic" named in the certificate. Generally, this means that a single client can only send push notifications to a single receiving app.

Once you've registered your app and have the requisite certificates, the first thing you'll need to do to start sending push notifications with Pushy is to create an [`ApnsClient`](http://relayrides.github.io/pushy/apidocs/0.13/com/turo/pushy/apns/ApnsClient.html). Clients using TLS authentication need a certificate and private key to authenticate with the APNs server. The most common way to store the certificate and key is in a password-protected PKCS#12 file (you'll wind up with a password-protected .p12 file if you follow Apple's instructions at the time of this writing). To create a client that will use TLS-based authentication:

```java
final ApnsClient apnsClient = new ApnsClientBuilder()
        .setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
        .setClientCredentials(new File("/path/to/certificate.p12"), "p12-file-password")
        .build();
```

### Token authentication

In token-based authentication, clients still connect to the server using a TLS-secured connection, but do *not* present a certificate to the server when connecting. Instead, clients include a cryptographically-signed token with each notification they send (don't worry—Pushy handles this for you automatically). Clients may send push notifications to any "topic" for which they have a valid signing key.

To get started with a token-based client, you'll need to get a signing key (also called a private key in some contexts) from Apple. Once you have your signing key, you can create a new client:

```java
final ApnsClient apnsClient = new ApnsClientBuilder()
        .setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
        .setSigningKey(ApnsSigningKey.loadFromPkcs8File(new File("/path/to/key.p8"),
                "TEAMID1234", "KEYID67890"))
        .build();
```

## Sending push notifications

Pushy's APNs clients maintain an internal pool of connections to the APNs server and create new connections on demand. As a result, clients do not need to be started explicitly. Regardless of the authentication method you choose, once you've created a client, it's ready to start sending push notifications. At minimum, [push notifications](http://relayrides.github.io/pushy/apidocs/0.13/com/turo/pushy/apns/ApnsPushNotification.html) need a device token (which identifies the notification's destination device and is a distinct idea from an authentication token), a topic, and a payload.

```java
final SimpleApnsPushNotification pushNotification;

{
    final ApnsPayloadBuilder payloadBuilder = new ApnsPayloadBuilder();
    payloadBuilder.setAlertBody("Example!");

    final String payload = payloadBuilder.buildWithDefaultMaximumLength();
    final String token = TokenUtil.sanitizeTokenString("<efc7492 bdbd8209>");

    pushNotification = new SimpleApnsPushNotification(token, "com.example.myApp", payload);
}
```

The process of sending a push notification is asynchronous; although the process of sending a notification and getting a reply from the server may take some time, the client will return a [`io.netty.util.concurrent.Future`](http://netty.io/4.1/api/io/netty/util/concurrent/Future.html) right away. You can use that `Future` to track the progress and eventual outcome of the sending operation. Note that an `io.netty.util.concurrent.Future` is an extension of the Java [`Future`](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/Future.html) interface that allows callers to add listeners and adds methods for checking the status of the `Future`.

```java
final PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>>
    sendNotificationFuture = apnsClient.sendNotification(pushNotification);
```

The `Future` will complete in one of three circumstances:

1. The gateway accepts the notification and will attempt to deliver it to the destination device.
2. The gateway rejects the notification; this should be considered a permanent failure, and the notification should not be sent again. Additionally, the APNs gateway may indicate a timestamp at which the destination token became invalid. If that happens, you should stop trying to send *any* notification to that token unless the token has been re-registered since that timestamp.
3. The `Future` fails with an exception. This should generally be considered a temporary failure, and callers should try to send the notification again when the problem has been resolved.

An example:

```java
try {
    final PushNotificationResponse<SimpleApnsPushNotification> pushNotificationResponse =
            sendNotificationFuture.get();

    if (pushNotificationResponse.isAccepted()) {
        System.out.println("Push notification accepted by APNs gateway.");
    } else {
        System.out.println("Notification rejected by the APNs gateway: " +
                pushNotificationResponse.getRejectionReason());

        if (pushNotificationResponse.getTokenInvalidationTimestamp() != null) {
            System.out.println("\t…and the token is invalid as of " +
                pushNotificationResponse.getTokenInvalidationTimestamp());
        }
    }
} catch (final ExecutionException e) {
    System.err.println("Failed to send push notification.");
    e.printStackTrace();
}
```

Again, it's important to note that the returned `Future` supports listeners; waiting for each individual push notification is inefficient in practice, and most users will be better serverd by adding a listener to the `Future` instead of blocking until it completes. As an example:

```java
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
```

All APNs clients—even those that have never sent a message—may allocate and hold on to system resources, and it's important to release them. APNs clients are intended to be persistent, long-lived resources; you definitely don't need to shut down a client after sending a notification (or even batch of notifications), but you'll want to shut down your client (or clients) when your application is shutting down:

```java
final Future<Void> closeFuture = apnsClient.close();
closeFuture.await();
```

When shutting down, clients will wait for all sent-but-not-acknowledged notifications to receive a reply from the server. Notifications that have been passed to `sendNotification` but not yet sent to the server (i.e. notifications waiting in an internal queue) will fail immediately when disconnecting. Callers should generally make sure that all sent notifications have been acknowledged by the server before shutting down.

## Performance and best practices

Making the most of your system resources for high-throughput applications always takes some effort. To guide you through the process, we've put together a wiki page covering some [best practices for using Pushy](https://github.com/relayrides/pushy/wiki/Best-practices). All of these points are covered in much more detail on the wiki, but in general, our recommendations are:

- Treat `ApnsClient` instances as long-lived resources
- Use listeners if you want to track the status of your push notifications
- Use a flow control strategy to avoid enqueueing push notifications faster than the server can respond
- Choose a number of threads and concurrent connections that balances CPU time and network throughput

## System requirements

Pushy requires Java 7 or newer. Under Java 7, Pushy depends on netty-tcnative as an SSL provider (it is included automatically by dependency management systems, but Java 7 users managing dependcies manually will need to make sure it's on their classpath). A native SSL provider is not required under Java 8 and newer, but users may still choose to include one for enhanced performance.

## Metrics

Pushy includes an interface for monitoring metrics that provide insight into clients' behavior and performance. You can write your own implementation of the `ApnsClientMetricsListener` interface to record and report metrics. We also provide metrics listeners that gather and report metrics [using the Dropwizard Metrics library](https://github.com/relayrides/pushy/tree/master/dropwizard-metrics-listener) and [using the Micrometer application monitoring facade](https://github.com/relayrides/pushy/tree/master/micrometer-metrics-listener) as separate modules. To begin receiving metrics, set a listener when building a new client:

```java
final ApnsClient apnsClient = new ApnsClientBuilder()
        .setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
        .setSigningKey(ApnsSigningKey.loadFromPkcs8File(new File("/path/to/key.p8"),
                "TEAMID1234", "KEYID67890"))
        .setMetricsListener(new MyCustomMetricsListener())
        .build();
```

Please note that the metric-handling methods in your listener implementation should *never* call blocking code. It's appropriate to increment counters directly in the handler methods, but calls to databases or remote monitoring endpoints should be dispatched to separate threads.

## Using a proxy

If you need to use a proxy for outbound connections, you may specify a [`ProxyHandlerFactory`](http://relayrides.github.io/pushy/apidocs/0.13/com/turo/pushy/apns/proxy/ProxyHandlerFactory.html) when building your `ApnsClient` instance. Concrete implementations of `ProxyHandlerFactory` are provided for HTTP, SOCKS4, and SOCKS5 proxies.

An example:

```java
final ApnsClient apnsClient = new ApnsClientBuilder()
    .setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
    .setSigningKey(ApnsSigningKey.loadFromPkcs8File(new File("/path/to/key.p8"),
            "TEAMID1234", "KEYID67890"))
    .setProxyHandlerFactory(new Socks5ProxyHandlerFactory(
        new InetSocketAddress("my.proxy.com", 1080), "username", "password"))
    .build();
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

## Using a mock server

Pushy includes a mock APNs server that callers may use in integration tests and benchmarks. It is not necessary to use a mock server (or any related classes) in normal operation.

To build a mock server, callers should use a [`MockApnsServerBuilder`](http://relayrides.github.io/pushy/apidocs/0.13/com/turo/pushy/apns/server/MockApnsServerBuilder.html). All servers require a [`PushNotificationHandler`](http://relayrides.github.io/pushy/apidocs/0.13/com/turo/pushy/apns/server/PushNotificationHandler.html) (built by a [`PushNotificationHandlerFactory`](http://relayrides.github.io/pushy/apidocs/0.13/com/turo/pushy/apns/server/PushNotificationHandlerFactory.html) provided to the builder) that decides whether the mock server will accept or reject each incoming push notification. Pushy includes an `AcceptAllPushNotificationHandlerFactory` that is helpful for benchmarking and a `ValidatingPushNotificationHandlerFactory` that may be helpful for integration testing.

Callers may also provide a [`MockApnsServerListener`](http://relayrides.github.io/pushy/apidocs/0.13/com/turo/pushy/apns/server/MockApnsServerListener.html) when building a mock server; listeners are notified whenever the mock server accepts or rejects a notification from a client.

## License and status

Pushy is available under the [MIT License](https://github.com/relayrides/pushy/blob/master/LICENSE.md).

The current version of Pushy is 0.13.6. We consider it to be fully functional (and use it in production!), but the public API may change significantly before a 1.0 release.
