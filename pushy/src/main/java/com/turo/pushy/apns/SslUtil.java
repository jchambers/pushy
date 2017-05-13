/*
 * Copyright (c) 2013-2017 Turo
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
                sslProvider = SslProvider.OPENSSL_REFCNT;
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
