package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class WebhookNotFoundException extends LinkException {

  public WebhookNotFoundException() {
    super("webhook not found");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public String code() {
    return "WEBHOOK_NOT_FOUND";
  }
}
