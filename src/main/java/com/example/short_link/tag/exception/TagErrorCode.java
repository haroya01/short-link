package com.example.short_link.tag.exception;

import org.springframework.http.HttpStatus;

public enum TagErrorCode {
  TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "tag not found: %s"),
  DUPLICATE_TAG_NAME(HttpStatus.CONFLICT, "tag already exists: %s");

  private final HttpStatus status;
  private final String template;

  TagErrorCode(HttpStatus status, String template) {
    this.status = status;
    this.template = template;
  }

  public HttpStatus status() {
    return status;
  }

  public String format(Object... args) {
    return args == null || args.length == 0 ? template : template.formatted(args);
  }

  public TagException raise(Object... args) {
    return new TagException(this, args);
  }
}
