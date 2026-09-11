package com.example.short_link.link.access.application;

/** Tracks failed guesses for one link and client, and resets them after successful verification. */
public interface PasswordAttempts {
  boolean isLockedOut(String shortCode, String clientIp);

  void recordFailure(String shortCode, String clientIp);

  void reset(String shortCode, String clientIp);
}
