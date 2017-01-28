package com.relayrides.pushy.apns.auth;

public interface ApnsKeyRemovalListener<T extends ApnsKey> {

    void handleKeyRemoval(final T key);
}
