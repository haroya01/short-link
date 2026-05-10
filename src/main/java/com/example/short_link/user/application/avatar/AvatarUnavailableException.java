package com.example.short_link.user.application.avatar;

public class AvatarUnavailableException extends RuntimeException {
  public AvatarUnavailableException() {
    super("avatar upload not configured");
  }
}
