package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class InvalidWebhookUrlException extends LinkException {

  public InvalidWebhookUrlException() {
    super("webhook url must be public http(s) and resolve to a non-private IP");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "INVALID_WEBHOOK_URL";
  }
}
