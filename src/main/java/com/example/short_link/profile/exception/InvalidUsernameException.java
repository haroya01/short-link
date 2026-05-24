package com.example.short_link.profile.exception;

public class InvalidUsernameException extends RuntimeException {
  public InvalidUsernameException(String reason) {
    super(reason);
  }
}
