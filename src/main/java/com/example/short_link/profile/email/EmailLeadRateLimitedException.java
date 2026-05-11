package com.example.short_link.profile.email;

public class EmailLeadRateLimitedException extends RuntimeException {
  public EmailLeadRateLimitedException(String message) {
    super(message);
  }
}
