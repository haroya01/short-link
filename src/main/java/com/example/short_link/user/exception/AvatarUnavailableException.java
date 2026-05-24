package com.example.short_link.user.exception;

public class AvatarUnavailableException extends RuntimeException {
  public AvatarUnavailableException() {
    super("avatar upload not configured");
  }
}
