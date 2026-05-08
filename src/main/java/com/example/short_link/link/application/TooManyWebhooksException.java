package com.example.short_link.link.application;

import lombok.Getter;

@Getter
public class TooManyWebhooksException extends RuntimeException {

  private final int limit;

  public TooManyWebhooksException(int limit) {
    super("too many webhooks for this link (max " + limit + ")");
    this.limit = limit;
  }
}
