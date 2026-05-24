package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class ShortCodeGenerationException extends LinkException {

  public ShortCodeGenerationException() {
    super("Failed to generate unique short code after multiple attempts");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.INTERNAL_SERVER_ERROR;
  }

  @Override
  public String code() {
    return "SHORT_CODE_EXHAUSTED";
  }
}
