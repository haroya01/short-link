package com.example.short_link.profile.exception;

import org.springframework.http.HttpStatus;

public final class ProfileNotFoundException extends ProfileException {

  public ProfileNotFoundException(String username) {
    super("profile not found: " + username);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public String code() {
    return "PROFILE_NOT_FOUND";
  }
}
