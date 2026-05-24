package com.example.short_link.profile.exception;

import org.springframework.http.HttpStatus;

public final class EmailLeadRateLimitedException extends ProfileException {

  public EmailLeadRateLimitedException(String message) {
    super(message);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.TOO_MANY_REQUESTS;
  }

  @Override
  public String code() {
    return "EMAIL_LEAD_RATE_LIMITED";
  }
}
