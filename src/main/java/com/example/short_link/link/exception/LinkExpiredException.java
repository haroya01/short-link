package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class LinkExpiredException extends LinkException {

  public LinkExpiredException(String shortCode) {
    super("link expired: " + shortCode);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.GONE;
  }

  @Override
  public String code() {
    return "LINK_EXPIRED";
  }
}
