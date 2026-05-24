package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class LinkNotOwnedException extends LinkException {

  public LinkNotOwnedException(String shortCode) {
    super("link not owned by current user: " + shortCode);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.FORBIDDEN;
  }

  @Override
  public String code() {
    return "LINK_NOT_OWNED";
  }
}
