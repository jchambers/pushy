package com.relayrides.pushy.apns;

import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for choosing SSL providers.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.10
 */
class SslUtil {

    private static final Logger log = LoggerFactory.getLogger(SslUtil.class);

    /**
     * Selects an SSL provider based on the availability of of an ALPN-capable native provider.
     *
     * @return an ALPN-capable native SSL provider if available, or else the JDK SSL provider
     */
    public static SslProvider getSslProvider() {
        final SslProvider sslProvider;

        if (OpenSsl.isAvailable()) {
            if (OpenSsl.isAlpnSupported()) {
                log.info("Native SSL provider is available and supports ALPN; will use native provider.");
                sslProvider = SslProvider.OPENSSL;
            } else {
                log.info("Native SSL provider is available, but does not support ALPN; will use JDK SSL provider.");
                sslProvider = SslProvider.JDK;
            }
        } else {
            log.info("Native SSL provider not available; will use JDK SSL provider.");
            sslProvider = SslProvider.JDK;
        }

        return sslProvider;
    }
}
