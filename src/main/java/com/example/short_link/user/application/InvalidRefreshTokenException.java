package com.example.short_link.user.application;

public class InvalidRefreshTokenException extends RuntimeException {

  public InvalidRefreshTokenException() {
    super("invalid or expired refresh token");
  }
}
