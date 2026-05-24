package com.example.short_link.profile.exception;

import org.springframework.http.HttpStatus;

public enum ProfileErrorCode {
  PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "profile not found: %s"),
  INVALID_USERNAME(HttpStatus.BAD_REQUEST, "%s"),
  USERNAME_TAKEN(HttpStatus.CONFLICT, "username taken: %s"),
  OEMBED_NOT_APPLICABLE(HttpStatus.UNPROCESSABLE_ENTITY, "oembed not applicable"),
  EMAIL_LEAD_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "%s");

  private final HttpStatus status;
  private final String template;

  ProfileErrorCode(HttpStatus status, String template) {
    this.status = status;
    this.template = template;
  }

  public HttpStatus status() {
    return status;
  }

  public String format(Object... args) {
    return args == null || args.length == 0 ? template : template.formatted(args);
  }
}
