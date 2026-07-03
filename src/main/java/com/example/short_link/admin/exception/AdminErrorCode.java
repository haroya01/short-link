package com.example.short_link.admin.exception;

import org.springframework.http.HttpStatus;

public enum AdminErrorCode {
  INVALID_ACTIVE_PERIOD(HttpStatus.BAD_REQUEST, "Invalid active-users period: %s"),
  INVALID_DOMAIN(HttpStatus.BAD_REQUEST, "invalid domain: %s"),
  INVALID_ROLE(HttpStatus.BAD_REQUEST, "Invalid role filter: %s"),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "No user with id %s");

  private final HttpStatus status;
  private final String template;

  AdminErrorCode(HttpStatus status, String template) {
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
