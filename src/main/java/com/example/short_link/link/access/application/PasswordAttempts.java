package com.example.short_link.link.access.application;

public interface PasswordAttempts {
  boolean isLockedOut(String shortCode, String clientIp);

  void recordFailure(String shortCode, String clientIp);

  void reset(String shortCode, String clientIp);
}
