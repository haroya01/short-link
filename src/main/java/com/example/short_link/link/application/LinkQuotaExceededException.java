package com.example.short_link.link.application;

public class LinkQuotaExceededException extends RuntimeException {

  private final long limit;

  public LinkQuotaExceededException(long limit) {
    super("link quota exceeded (limit=" + limit + ")");
    this.limit = limit;
  }

  public long getLimit() {
    return limit;
  }
}
