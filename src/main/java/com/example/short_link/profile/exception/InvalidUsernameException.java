package com.example.short_link.profile.exception;

import org.springframework.http.HttpStatus;

public final class InvalidUsernameException extends ProfileException {

  public InvalidUsernameException(String reason) {
    super(reason);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "INVALID_USERNAME";
  }
}
