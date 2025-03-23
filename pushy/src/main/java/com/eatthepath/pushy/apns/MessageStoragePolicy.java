package com.eatthepath.pushy.apns;

public enum MessageStoragePolicy {
  NO_MESSAGE_STORED(0),
  MOST_RECENT_MESSAGE_STORED(1);

  private final int code;

  MessageStoragePolicy(final int code) {
    this.code = code;
  }

  public int getCode() {
    return this.code;
  }

  public static MessageStoragePolicy getFromCode(final int code) {
    for (final MessageStoragePolicy policy : MessageStoragePolicy.values()) {
      if (policy.getCode() == code) {
        return policy;
      }
    }

    throw new IllegalArgumentException(String.format("No message storage policy found with code %d", code));
  }
}
