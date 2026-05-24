package com.example.short_link.user.exception;

import org.springframework.http.HttpStatus;

public final class InvalidTimezoneException extends UserException {

  public InvalidTimezoneException(String timezone) {
    super("Invalid timezone: " + timezone);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "INVALID_TIMEZONE";
  }
}
