package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class LinkQuotaExceededException extends LinkException {

  private final long limit;

  public LinkQuotaExceededException(long limit) {
    super("link quota exceeded (limit=" + limit + ")");
    this.limit = limit;
  }

  public long getLimit() {
    return limit;
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.CONFLICT;
  }

  @Override
  public String code() {
    return "LINK_QUOTA_EXCEEDED";
  }
}
