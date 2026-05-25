package com.example.short_link.admin.exception;

import org.springframework.http.HttpStatus;

public enum AdminErrorCode {
  INVALID_ACTIVE_PERIOD(HttpStatus.BAD_REQUEST, "Invalid active-users period: %s");

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
