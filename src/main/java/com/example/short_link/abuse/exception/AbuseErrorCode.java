package com.example.short_link.abuse.exception;

import org.springframework.http.HttpStatus;

public enum AbuseErrorCode {
  ABUSE_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "abuse report not found: %s"),
  ALREADY_RESOLVED(HttpStatus.CONFLICT, "report already resolved: %s"),
  SUBJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "reported subject not found: %s"),
  DUPLICATE_REPORT(HttpStatus.CONFLICT, "you already have an open report for this subject"),
  ACTION_SUBJECT_MISMATCH(HttpStatus.BAD_REQUEST, "action does not apply to this subject type: %s"),
  SUSPEND_REQUIRES_EXPIRY(
      HttpStatus.BAD_REQUEST, "SUSPEND_USER requires suspendUntil in the future"),
  REASON_REQUIRED(HttpStatus.BAD_REQUEST, "reasonCode (or legacy reason) is required");

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
