# pushy

[![Build Status](https://travis-ci.org/relayrides/pushy.svg?branch=master)](https://travis-ci.org/relayrides/pushy)

Pushy is a Java library for sending [APNs](https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ApplePushService.html) (iOS and OS X) push notifications. It is written and maintained by the engineers at [Turo](https://turo.com/).

Pushy sends push notifications using Apple's HTTP/2-based APNs protocol. It distinguishes itself from other push notification libraries with a focus on [thorough documentation](http://relayrides.github.io/pushy/apidocs/0.6/), asynchronous operation, and design for industrial-scale operation; with Pushy, it's easy and efficient to maintain multiple parallel connections to the APNs gateway to send large numbers of notifications to many different applications ("topics").

We believe that Pushy is already the best tool for sending APNs push notifications from Java applications, and we hope you'll help us make it even better via bug reports and pull requests. If you have questions about using Pushy, please join us on [the Pushy mailing list](https://groups.google.com/d/forum/pushy-apns) or take a look at [the wiki](https://github.com/relayrides/pushy/wiki). Thanks!

## Getting Pushy

If you use [Maven](http://maven.apache.org/), you can add Pushy to your project by adding the following dependency declaration to your POM:

```xml
<dependency>
    <groupId>com.relayrides</groupId>
    <artifactId>pushy</artifactId>
    <version>0.6.1</version>
</dependency>
```

If you don't use Maven (or something else that understands Maven dependencies, like Gradle), you can [download Pushy as a `.jar` file](https://github.com/relayrides/pushy/releases/download/pushy-0.6.1/pushy-0.6.1.jar) and add it to your project directly. You'll also need to make sure you have Pushy's runtime dependencies on your classpath. They are:

- [netty 4.1.0](http://netty.io/)
- [gson 2.5](https://github.com/google/gson)
- [slf4j 1.7.6](http://www.slf4j.org/) (and possibly an SLF4J binding, as described in the [logging](#logging) section below)
- Either `netty-tcnative` or `alpn-boot`, as discussed in the [system requirements](#system-requirements) section below
  - [alpn-api](http://www.eclipse.org/jetty/documentation/current/alpn-chapter.html) if you've opted not to use `alpn-boot` (`alpn-api` is included in `alpn-boot`); please see the [system requirements](#system-requirements) section for details)

Pushy itself requires Java 7 or newer to build and run.

## Sending push notifications

Before you can get started with Pushy, you'll need to do some provisioning work with Apple to register your app and get the required certificates. For details on this process, please see the [Provisioning and Development](https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ProvisioningDevelopment.html#//apple_ref/doc/uid/TP40008194-CH104-SW1) section of Apple's official documentation. Please note that there are [some caveats](https://github.com/relayrides/pushy/wiki/Certificates), particularly under Mac OS X 10.11 (El Capitan).

Once you've registered your app and have the requisite certificates, the first thing you'll need to do to start sending push notifications with Pushy is to create an [`ApnsClient`](http://relayrides.github.io/pushy/apidocs/0.6/com/relayrides/pushy/apns/ApnsClient.html). Clients need a certificate and private key to authenticate with the APNs server. The most common way to store the certificate and key is in a password-protected PKCS#12 file (you'll wind up with a password-protected .p12 file if you follow Apple's instructions at the time of this writing):

```java
final ApnsClient<SimpleApnsPushNotification> apnsClient = new ApnsClient<>(
        new File("/path/to/certificate.p12"), "p12-file-password");
```

Once you've created a client, you can connect it to the APNs gateway. Note that this process is asynchronous; the client will return a Future right away, but you'll need to wait for it to complete before you can send any notifications. Note that this is a Netty [`Future`](http://netty.io/4.1/api/io/netty/util/concurrent/Future.html), which is an extension of the Java [`Future`](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/Future.html) interface that allows callers to add listeners and adds methods for checking the status of the `Future`.

```java
final Future<Void> connectFuture = apnsClient.connect(ApnsClient.DEVELOPMENT_APNS_HOST);
connectFuture.await();
```

Once the client has finished connecting to the APNs server, you can begin sending push notifications. At a minimum, [push notifications](http://relayrides.github.io/pushy/apidocs/0.6/com/relayrides/pushy/apns/ApnsPushNotification.html) need a destination token, a topic, and a payload.

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

Like connecting, sending notifications is an asynchronous process. You'll get a `Future` immediately, but will need to wait for the `Future` to complete before you'll know whether the notification was accepted or rejected by the APNs gateway.

```java
final Future<PushNotificationResponse<SimpleApnsPushNotification>> sendNotificationFuture =
        apnsClient.sendNotification(pushNotification);
```

The `Future` will complete in one of three circumstances:

1. The gateway accepts the notification and will attempt to deliver it to the destination device.
2. The gateway rejects the notification; this should be considered a permanent failure, and the notification should not be sent again. Additionally, the APNs gateway may indicate [a timestamp at which the destination token became invalid](https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/APNsProviderAPI.html#//apple_ref/doc/uid/TP40008194-CH101-SW18). If that happens, you should stop trying to send *any* notification to that token unless the token has been re-registered since that timestamp.
3. The `Future` fails with an exception. This should generally be considered a temporary failure, and callers should try to send the notification again when the problem has been resolved. In particular, the `Future` may fail with a [`ClientNotConnectedException`](http://relayrides.github.io/pushy/apidocs/0.6/com/relayrides/pushy/apns/ClientNotConnectedException.html), in which case callers may wait for the connection to be restored automatically by waiting for the `Future` returned by [`ApnsClient#getReconnectionFuture()`](http://relayrides.github.io/pushy/apidocs/0.6/com/relayrides/pushy/apns/ApnsClient.html#getReconnectionFuture--).

An example:

```java
try {
    final PushNotificationResponse<SimpleApnsPushNotification> pushNotificationResponse =
            sendNotificationFuture.get();

    if (pushNotificationResponse.isAccepted()) {
        System.out.println("Push notitification accepted by APNs gateway.");
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

    if (e.getCause() instanceof ClientNotConnectedException) {
        System.out.println("Waiting for client to reconnect…");
        apnsClient.getReconnectionFuture().await();
        System.out.println("Reconnected.");
    }
}
```

Again, it's important to note that the returned `Future` supports listeners; waiting for each individual push notification is inefficient in practice, and most users will be better serverd by adding a listener to the `Future` instead of blocking until it completes.

Finally, when your application is shutting down, you'll want to disconnect any active clients:

```java
final Future<Void> disconnectFuture = apnsClient.disconnect();
disconnectFuture.await();
```

## System requirements

Pushy works with Java 7 and newer, but has some additional dependencies depending on the environment in which it is running.

The APNs protocol is built on top of the [HTTP/2 protocol](https://http2.github.io/). HTTP/2 is a relatively new protocol, and relies on some new developments that aren't yet wide-spread in the Java world. In particular:

1. HTTP/2 depends on [ALPN](https://tools.ietf.org/html/rfc7301), a TLS extension for protocol negotiation. No version of Java has native ALPN support at this time. The ALPN requirement may be met either by [using OpenSSL as an SSL provider](#using-native-openssl-as-an-ssl-provider) or by [using Jetty's ALPN implementation](#using-jettys-alpn-implementation) under OpenJDK 7 or 8.
2. The HTTP/2 specification requires the use of [ciphers](https://httpwg.github.io/specs/rfc7540.html#rfc.section.9.2.2) that weren't introduced in Java until Java 8. Using OpenSSL as an SSL provider is the best way to meet this requirement under Java 7. Using OpenSSL isn't a requirement under Java 8, but may still yield performance gains.

Generally speaking, using OpenSSL as your SSL provider is the best way to fulfill the system requirements imposed by HTTP/2 because installation is fairly straightforward, it works for Java 7 onward and generally offers better SSL performance than the JDK SSL provider.

### Using OpenSSL as an SSL provider

Using OpenSSL as an SSL provider fulfills the ALPN and cipher suite requirements imposed by HTTP/2. To use OpenSSL as an SSL provider, you'll need OpenSSL 1.0.2 or newer installed. You'll also need to add `netty-tcnative` as a dependency to your project. The `netty-tcnative` wiki provides [detailed instructions](http://netty.io/wiki/forked-tomcat-native.html), but in short, you'll need to add one additional platform-specific dependency to your project. This approach will meet all requirements imposed by HTTP/2 under Java 7 and 8.

Additionally, you'll need [`alpn-api`](http://mvnrepository.com/artifact/org.eclipse.jetty.alpn/alpn-api) as a `runtime` dependency for your project. If you're managing dependencies manually, you'll just need to make sure the latest version of `alpn-api` is available on your classpath.

Please note that, at least as recently as OS X 10.11 (El Capitan), Mac OS X did not ship with a version of OpenSSL that supports ALPN or the cipher suites required by HTTP/2. If you're using Mac OS X and intend to use OpenSSL as your SSL provider, you'll need to update OpenSSL 1.0.2 or newer.

### Using Jetty's ALPN implementation

As an alternative to OpenSSL, you may use Jetty's ALPN implementation if you're using OpenJDK 8. Please note that if you're not using Java 8 or newer, you'll need to meet the cipher suite requirement separately; you may do so either by using OpenSSL (which also fulfills the ALPN requirement) or by using another cryptography provider (which is beyond the scope of this README).

Using Jetty's ALPN implementation is somewhat more complicated than using OpenSSL as an SSL provider. You'll need to choose a version of `alpn-boot` specific to the version (down to the update!) of OpenJDK you're using, and then add it to your *boot* class path. [Detailed instructions](http://www.eclipse.org/jetty/documentation/current/alpn-chapter.html) are provided by Jetty.

If you know exactly which version of Java you'll be running, you can just add that specific version of `alpn-boot` to your boot class path. If your project might run on a number of different systems and if you use Maven, you can use the [`jetty-alpn-agent`](https://github.com/jetty-project/jetty-alpn-agent) in your `pom.xml`, which loads the correct `alpn-boot` JAR file for the current Java version on the fly.

## Metrics

Pushy includes an interface for monitoring metrics that provide insight into clients' behavior and performance. You can write your own implementation of the `ApnsClientMetricsListener` interface to record and report metrics. To begin receiving metrics, use the client's `setMetricsListener` method:

```java
apnsClient.setMetricsListener(new MyCustomMetricsListener());
```

Please note that the metric-handling methods in your listener implementation should *never* call blocking code. It's appropriate to increment counters directly in the handler methods, but calls to databases or remote monitoring endpoints should be dispatched to separate threads.

## Using a proxy

If you need to use a proxy for outbound connections, you may specify a [`ProxyHandlerFactory`](http://relayrides.github.io/pushy/apidocs/0.6/com/relayrides/pushy/apns/proxy/ProxyHandlerFactory.html) before attempting to connect your `ApnsClient` instance. Concrete implementations of `ProxyHandlerFactory` are provided for HTTP, SOCKS4, and SOCKS5 proxies.

An example:

```java
final ApnsClient<SimpleApnsPushNotification> apnsClient =
        new ApnsClient<SimpleApnsPushNotification>(
                new File("/path/to/certificate.p12"), "p12-file-password");

apnsClient.setProxyHandlerFactory(
        new Socks5ProxyHandlerFactory(
                new InetSocketAddress("my.proxy.com", 1080), "username", "password"));

final Future<Void> connectFuture = apnsClient.connect(ApnsClient.DEVELOPMENT_APNS_HOST);
connectFuture.await();
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

## Known issues

Although we make every effort to fix bugs and work around issues outside of our control, some problems appear to be unavoidable. The issues we know about at this time are:

- Pushy will work just fine in a container environment (like Tomcat), but Netty (the networking framework on which Pushy is built) creates some `ThreadLocal` instances that won't be cleaned up properly when your application shuts down. Most application containers have features designed to mitigate this kind of issue, but leaks are still possible. Users should be aware of the issue if using Pushy in an application container. See [#73](https://github.com/relayrides/pushy/issues/73) for additional discussion.

## License and status

Pushy is available under the [MIT License](http://opensource.org/licenses/MIT).

The current version of Pushy is 0.6.1. We consider it to be fully functional (and use it in production!), but the public API may change significantly before a 1.0 release.
