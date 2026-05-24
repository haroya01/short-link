package com.example.short_link.user.exception;

import org.springframework.http.HttpStatus;

public final class TwoFactorStateException extends UserException {

  public TwoFactorStateException(String message) {
    super(message);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.CONFLICT;
  }

  @Override
  public String code() {
    return "TWO_FACTOR_STATE";
  }
}
