package com.example.short_link.link.exception;

public class WebhookNotFoundException extends RuntimeException {

  public WebhookNotFoundException() {
    super("webhook not found");
  }
}
