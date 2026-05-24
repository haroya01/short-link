package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class LinkViewLimitExceededException extends LinkException {

  public LinkViewLimitExceededException(String shortCode) {
    super("link view limit exceeded: " + shortCode);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.GONE;
  }

  @Override
  public String code() {
    return "LINK_VIEW_LIMIT_EXCEEDED";
  }
}
