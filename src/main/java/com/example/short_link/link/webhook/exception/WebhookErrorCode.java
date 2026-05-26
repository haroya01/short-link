package com.example.short_link.link.webhook.exception;

import org.springframework.http.HttpStatus;

public enum WebhookErrorCode {
  INVALID_WEBHOOK_URL(
      HttpStatus.BAD_REQUEST, "webhook url must be public http(s) and resolve to a non-private IP"),
  TOO_MANY_WEBHOOKS(HttpStatus.CONFLICT, "too many webhooks for this link (max %d)", "limit"),
  WEBHOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "webhook not found");

  private final HttpStatus status;
  private final String template;
  private final String[] metadataKeys;

  WebhookErrorCode(HttpStatus status, String template, String... metadataKeys) {
    this.status = status;
    this.template = template;
    this.metadataKeys = metadataKeys;
  }

  public HttpStatus status() {
    return status;
  }

  public String[] metadataKeys() {
    return metadataKeys;
  }

  public String format(Object... args) {
    return args == null || args.length == 0 ? template : template.formatted(args);
  }
}
