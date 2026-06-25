package com.example.short_link.common.web;

/**
 * Thrown when a request body exceeds its route's byte cap, including when Content-Length is absent.
 */
public class PayloadTooLargeException extends RuntimeException {

  private final long limit;

  public PayloadTooLargeException(long limit) {
    super("request body exceeds " + limit + " bytes");
    this.limit = limit;
  }

  public long limit() {
    return limit;
  }
}
