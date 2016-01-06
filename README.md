# pushy

[![Build Status](https://travis-ci.org/relayrides/pushy.svg?branch=master)](https://travis-ci.org/relayrides/pushy)

Pushy is a Java library for sending [APNs](https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ApplePushService.html) (iOS and OS X) push notifications. It is written and maintained by the engineers at [Turo](https://turo.com/).

We believe that Pushy is already the best tool for sending APNs push notifications from Java applications, and we hope you'll help us make it even better via bug reports and pull requests. If you have questions about using Pushy, please join us on [the Pushy mailing list](https://groups.google.com/d/forum/pushy-apns) or take a look at [the wiki](https://github.com/relayrides/pushy/wiki). Thanks!

## Getting Pushy

If you use [Maven](http://maven.apache.org/), you can add Pushy to your project by adding the following dependency declaration to your POM:

```xml
<dependency>
    <groupId>com.relayrides</groupId>
    <artifactId>pushy</artifactId>
    <version>0.4.3</version>
</dependency>
```

If you don't use Maven (or something else that understands Maven dependencies, like Gradle), you can [download Pushy as a `.jar` file](https://github.com/relayrides/pushy/releases/download/pushy-0.4/pushy-0.4.jar) and add it to your project directly. You'll also need to make sure you have Pushy's runtime dependencies on your classpath. They are:

- [netty 4.1.0](http://netty.io/)
- [slf4j 1.7.6](http://www.slf4j.org/)
- [gson 2.5](https://github.com/google/gson)
- Either `netty-tcnative` or `alpn-boot`, as discussed in the [system requirements](https://github.com/relayrides/pushy#system-requirements) section below

Pushy itself requires Java 1.6 or newer to build and run.

## Sending push notifications

TODO

## System requirements

The APNs protocol is built on top of the [HTTP/2 protocol](https://http2.github.io/). HTTP/2 is a relatively new protocol, and relies on some new technological developments that aren't yet wide-spread in the Java world. In particular:

1. HTTP/2 depends on [ALPN](https://tools.ietf.org/html/rfc7301), a TLS extension for protocol negotiation. No version of Java has native ALPN support at this time. The ALPN requirement may be met either by using [native OpenSSL](http://netty.io/wiki/forked-tomcat-native.html) as an SSL provider for Java 6, 7, and 8, or by using [Jetty's ALPN implementation](http://www.eclipse.org/jetty/documentation/9.2.8.v20150217/alpn-chapter.html) under OpenJDK 7 or 8.
2. The HTTP/2 specification requires the use of [ciphers](https://httpwg.github.io/specs/rfc7540.html#rfc.section.9.2.2) that weren't introduced in Java until Java 8. Using [native OpenSSL](http://netty.io/wiki/forked-tomcat-native.html) as an SSL provider is the best way to meet this requirement under Java 6 and 7. Using native OpenSSL isn't a requirement under Java 8, but may still yield performance gains.

Generally speaking, using native OpenSSL as your SSL provider is the best way to fulfill the system requirements imposed by HTTP/2 because installation is fairly straightforward, it works for Java 6 onward and generally offers better SSL performance than the JDK SSL provider.

### Using native OpenSSL as an SSL provider

Using OpenSSL as an SSL provider fulfills the ALPN and cipher suite requirements imposed by HTTP/2. To use OpenSSL as an SSL provider, you'll need OpenSSL 1.0.2 or newer installed. You'll also need to add `netty-tcnative` as a dependency to your project. The `netty-tcnative` wiki provides [detailed instructions](http://netty.io/wiki/forked-tomcat-native.html), but in short, you'll need to add one additional platform-specific dependency to your project. This approach will meet all requirements imposed by HTTP/2 for Java 6, 7, and 8.

Please note that, at least as recently as OS X 10.11 (El Capitan), Mac OS X did not ship with a version of OpenSSL that supports ALPN or the cipher suites required by HTTP/2. If you're using Mac OS X and intend to use OpenSSL as your SSL provider, you'll need to update OpenSSL 1.0.2 or newer.

### Using Jetty's ALPN implementation

As an alternative to OpenSSL, you may use Jetty's ALPN implementation if you're using OpenJDK 8. Please note that if you're not using Java 8 or newer, you'll need to meet the cipher suite requirement separately; you may do so either by using OpenSSL (which also fulfills the ALPN requirement) or by using another cryptography provider (which is beyond the scope of this README).

Using Jetty's ALPN implementation is somewhat more complicated than using using OpenSSL as an SSL provider. You'll need to choose a version of `alpn-boot` specific to the version (down to the update!) of OpenJDK you're using, and then add it to your *boot* class path. You'll also need to add `alpn-api` as a normal dependency to your project. [Detailed instructions](http://www.eclipse.org/jetty/documentation/current/alpn-chapter.html) are provided by Jetty.

TODO: Include an example pom.

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

## License and status

Pushy is available to the public under the [MIT License](http://opensource.org/licenses/MIT).

The current version of Pushy is 0.4.3. We consider it to be fully functional (and use it in production!), but the public API may change significantly before a 1.0 release.
