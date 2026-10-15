package com.example.short_link.blogimport.exception;

import org.springframework.http.HttpStatus;

public enum BlogImportErrorCode {
  IMPORT_UNSUPPORTED_SOURCE(
      HttpStatus.BAD_REQUEST,
      "only Naver Blog, Tistory, velog and brunch addresses can be brought in: %s"),
  IMPORT_FEED_NOT_FOUND(HttpStatus.NOT_FOUND, "no feed was found for %s"),
  IMPORT_SOURCE_UNREACHABLE(HttpStatus.BAD_GATEWAY, "the blog could not be read: %s"),
  IMPORT_SOURCE_TAKEN(HttpStatus.CONFLICT, "another member already verified this blog"),
  IMPORT_VERIFICATION_CODE_MISSING(
      HttpStatus.UNPROCESSABLE_ENTITY, "the blog description does not contain %s yet"),
  IMPORT_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "no import source %s"),
  IMPORT_SOURCE_LIMIT(HttpStatus.CONFLICT, "up to %s blogs can be connected");

  private final HttpStatus status;
  private final String template;

  BlogImportErrorCode(HttpStatus status, String template) {
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
