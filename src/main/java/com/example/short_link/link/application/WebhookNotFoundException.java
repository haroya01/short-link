package com.example.short_link.link.application;

public class WebhookNotFoundException extends RuntimeException {

  public WebhookNotFoundException() {
    super("webhook not found");
  }
}
