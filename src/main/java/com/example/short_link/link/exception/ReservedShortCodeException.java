package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class ReservedShortCodeException extends LinkException {

  public ReservedShortCodeException(String code) {
    super("short code is reserved: " + code);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "RESERVED_SHORT_CODE";
  }
}
