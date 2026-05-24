package com.example.short_link.user.exception;

public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException() {
    super("user not found");
  }
}
