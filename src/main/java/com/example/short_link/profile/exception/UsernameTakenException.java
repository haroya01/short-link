package com.example.short_link.profile.exception;

public class UsernameTakenException extends RuntimeException {
  public UsernameTakenException(String username) {
    super("username taken: " + username);
  }
}
