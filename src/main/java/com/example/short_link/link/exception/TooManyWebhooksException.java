package com.example.short_link.link.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public final class TooManyWebhooksException extends LinkException {

  private final int limit;

  public TooManyWebhooksException(int limit) {
    super("too many webhooks for this link (max " + limit + ")");
    this.limit = limit;
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.CONFLICT;
  }

  @Override
  public String code() {
    return "TOO_MANY_WEBHOOKS";
  }
}
