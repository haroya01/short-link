package com.example.short_link.profile.exception;

import org.springframework.http.HttpStatus;

public final class UsernameTakenException extends ProfileException {

  public UsernameTakenException(String username) {
    super("username taken: " + username);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.CONFLICT;
  }

  @Override
  public String code() {
    return "USERNAME_TAKEN";
  }
}
