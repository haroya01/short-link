package com.example.short_link.user.exception;

import org.springframework.http.HttpStatus;

public final class InvalidTotpCodeException extends UserException {

  public InvalidTotpCodeException() {
    super("invalid TOTP or recovery code");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.UNAUTHORIZED;
  }

  @Override
  public String code() {
    return "INVALID_TOTP";
  }
}
