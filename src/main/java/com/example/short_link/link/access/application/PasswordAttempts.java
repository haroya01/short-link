package com.example.short_link.link.access.application;

public interface PasswordAttempts {
  // Counts this attempt before the password is checked; false once the window's limit is passed.
  boolean tryAttempt(String shortCode, String clientIp);

  void reset(String shortCode, String clientIp);
}
