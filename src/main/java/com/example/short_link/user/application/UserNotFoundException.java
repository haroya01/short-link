package com.example.short_link.user.application;

public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException() {
    super("user not found");
  }
}
