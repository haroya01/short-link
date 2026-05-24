package com.example.short_link.profile.exception;

public class EmailLeadRateLimitedException extends RuntimeException {
  public EmailLeadRateLimitedException(String message) {
    super(message);
  }
}
