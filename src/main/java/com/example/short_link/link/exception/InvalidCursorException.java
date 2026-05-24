package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class InvalidCursorException extends LinkException {

  public InvalidCursorException() {
    super("Invalid cursor");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "INVALID_CURSOR";
  }
}
