package com.relayrides.pushy.apns;

public interface ApnsKeyRemovalListener<T extends ApnsKey> {

    void handleKeyRemoval(final T key);
}
