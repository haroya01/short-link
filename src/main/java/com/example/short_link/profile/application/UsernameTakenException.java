package com.example.short_link.profile.application;

public class UsernameTakenException extends RuntimeException {
  public UsernameTakenException(String username) {
    super("username taken: " + username);
  }
}
