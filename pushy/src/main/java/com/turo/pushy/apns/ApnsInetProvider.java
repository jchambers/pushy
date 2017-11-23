package com.turo.pushy.apns;

import java.net.InetSocketAddress;

/**
 * interface for getting inetaddress for ApnsClient
 * we can implement several ways to get it:
 * static(simple), dynamic, or with performance detection(not implement yet)
 * @author hblzxsj
 */
public interface ApnsInetProvider {
    public InetSocketAddress getInetAddress();
}
