package com.example.short_link.abuse.exception;

import org.springframework.http.HttpStatus;

public enum AbuseErrorCode {
  ABUSE_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "abuse report not found: %s"),
  ALREADY_RESOLVED(HttpStatus.CONFLICT, "report already resolved: %s");

  private final HttpStatus status;
  private final String template;

  AbuseErrorCode(HttpStatus status, String template) {
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
