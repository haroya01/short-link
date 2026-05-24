package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class TagNotFoundException extends LinkException {

  public TagNotFoundException(Long id) {
    super("tag not found: " + id);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public String code() {
    return "TAG_NOT_FOUND";
  }
}
