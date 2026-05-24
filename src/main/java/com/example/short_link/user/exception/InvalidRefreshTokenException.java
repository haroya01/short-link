package com.example.short_link.user.exception;

import org.springframework.http.HttpStatus;

public final class InvalidRefreshTokenException extends UserException {

  public InvalidRefreshTokenException() {
    super("invalid or expired refresh token");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.UNAUTHORIZED;
  }

  @Override
  public String code() {
    return "INVALID_REFRESH_TOKEN";
  }
}
