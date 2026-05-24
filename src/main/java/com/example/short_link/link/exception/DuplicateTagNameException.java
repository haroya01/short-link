package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class DuplicateTagNameException extends LinkException {

  public DuplicateTagNameException(String name) {
    super("tag already exists: " + name);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.CONFLICT;
  }

  @Override
  public String code() {
    return "DUPLICATE_TAG_NAME";
  }
}
