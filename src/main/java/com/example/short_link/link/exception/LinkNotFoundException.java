package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class LinkNotFoundException extends LinkException {

  public LinkNotFoundException(String shortCode) {
    super("link not found: " + shortCode);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public String code() {
    return "LINK_NOT_FOUND";
  }
}
