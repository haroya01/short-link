package com.example.short_link.portability.exception;

import org.springframework.http.HttpStatus;

public enum PortabilityErrorCode {
  IMPORT_KIND_UNKNOWN(HttpStatus.BAD_REQUEST, "no import called %s"),
  IMPORT_FILE_EMPTY(HttpStatus.BAD_REQUEST, "the file has no lines to import"),
  IMPORT_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "an import takes up to %s lines"),
  IMPORT_RUNNING(HttpStatus.CONFLICT, "an import is still running");

  private final HttpStatus status;
  private final String template;

  PortabilityErrorCode(HttpStatus status, String template) {
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
