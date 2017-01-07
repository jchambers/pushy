package com.relayrides.pushy.apns;

import java.util.Date;
import java.util.Map;

class MockApnsServerHandlerConfiguration {
    private final boolean emulateInternalServerErrors;
    private final Map<String, Map<String, Date>> tokenExpirationsByTopic;

    public MockApnsServerHandlerConfiguration(final boolean emulateInternalServerErrors,
            final Map<String, Map<String, Date>> tokenExpirationsByTopic,
            final ApnsKeyRegistry<ApnsVerificationKey> verificationKeyRegistry) {

        this.emulateInternalServerErrors = emulateInternalServerErrors;
        this.tokenExpirationsByTopic = tokenExpirationsByTopic;
    }

    public boolean shouldEmulateInternalServerErrors() {
        return this.emulateInternalServerErrors;
    }

    public Map<String, Map<String, Date>> getTokenExpirationsByTopic() {
        return this.tokenExpirationsByTopic;
    }
}
