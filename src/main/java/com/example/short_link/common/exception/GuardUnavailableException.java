package com.example.short_link.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class GuardUnavailableException extends RuntimeException implements DomainException {

  public static final int RETRY_AFTER_SECONDS = 30;

  public GuardUnavailableException(Throwable cause) {
    super("abuse guard unavailable, try again shortly", cause);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.SERVICE_UNAVAILABLE;
  }

  @Override
  public String code() {
    return "TEMPORARILY_UNAVAILABLE";
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("retryAfterSeconds", RETRY_AFTER_SECONDS);
  }
}
