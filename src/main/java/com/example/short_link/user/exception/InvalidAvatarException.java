package com.example.short_link.user.exception;

import org.springframework.http.HttpStatus;

public final class InvalidAvatarException extends UserException {

  public InvalidAvatarException(String message) {
    super(message);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "INVALID_AVATAR";
  }
}
