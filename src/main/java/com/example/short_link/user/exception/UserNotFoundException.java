package com.example.short_link.user.exception;

import org.springframework.http.HttpStatus;

public final class UserNotFoundException extends UserException {

  public UserNotFoundException() {
    super("user not found");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public String code() {
    return "USER_NOT_FOUND";
  }
}
