/* Copyright (c) 2013-2016 RelayRides
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
 * THE SOFTWARE. */

/**
 * <p>Contains classes and interfaces for working with proxies.</p>
 *
 * <p>While {@link com.relayrides.pushy.apns.ApnsClient}s will connect to an APNs server directly by default, they may
 * optionally be configured to connect through a proxy by setting a
 * {@link com.relayrides.pushy.apns.proxy.ProxyHandlerFactory} via the
 * {@link com.relayrides.pushy.apns.ApnsClient#setProxyHandlerFactory(ProxyHandlerFactory)} method. Proxy handler
 * factory implementations are provided for HTTP, SOCKS4, and SOCKS5 proxies.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
package com.relayrides.pushy.apns.proxy;
