package com.example.short_link.profile.exception;

public class ProfileNotFoundException extends RuntimeException {
  public ProfileNotFoundException(String username) {
    super("profile not found: " + username);
  }
}
