package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class DuplicateShortCodeException extends LinkException {

  public DuplicateShortCodeException(String shortCode) {
    super("short code already exists: " + shortCode);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.CONFLICT;
  }

  @Override
  public String code() {
    return "DUPLICATE_SHORT_CODE";
  }
}
