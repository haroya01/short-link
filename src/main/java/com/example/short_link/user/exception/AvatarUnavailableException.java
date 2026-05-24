package com.example.short_link.user.exception;

import org.springframework.http.HttpStatus;

public final class AvatarUnavailableException extends UserException {

  public AvatarUnavailableException() {
    super("avatar upload not configured");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.SERVICE_UNAVAILABLE;
  }

  @Override
  public String code() {
    return "AVATAR_UNAVAILABLE";
  }
}
