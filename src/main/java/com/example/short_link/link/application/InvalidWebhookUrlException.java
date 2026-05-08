package com.example.short_link.link.application;

public class InvalidWebhookUrlException extends RuntimeException {

  public InvalidWebhookUrlException() {
    super("webhook url must be public http(s) and resolve to a non-private IP");
  }
}
