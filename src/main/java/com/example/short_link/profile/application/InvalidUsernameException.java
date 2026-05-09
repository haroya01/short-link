package com.example.short_link.profile.application;

public class InvalidUsernameException extends RuntimeException {
  public InvalidUsernameException(String reason) {
    super(reason);
  }
}
