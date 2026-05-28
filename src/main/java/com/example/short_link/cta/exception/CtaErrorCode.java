package com.example.short_link.cta.exception;

import org.springframework.http.HttpStatus;

public enum CtaErrorCode {
  CTA_NOT_FOUND(HttpStatus.NOT_FOUND, "cta not found: %s"),
  CTA_DELETED(HttpStatus.CONFLICT, "cta already deleted: %s"),
  CTA_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "cta permission denied");

  private final HttpStatus status;
  private final String template;

  CtaErrorCode(HttpStatus status, String template) {
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
