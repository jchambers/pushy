package com.turo.pushy.apns;

import java.net.InetSocketAddress;

public class SimpleInetProvider implements ApnsInetProvider {
    private InetSocketAddress address;
    
    public SimpleInetProvider(String host, int port) {
        address = new InetSocketAddress(host, port);
    }

    @Override
    public InetSocketAddress getInetAddress() {
        return address;
    }

}
