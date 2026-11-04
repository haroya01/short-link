package com.example.short_link.live.exception;

import org.springframework.http.HttpStatus;

public enum LiveErrorCode {
  LIVE_SCOPE_NOT_FOUND(HttpStatus.NOT_FOUND, "nothing to watch here"),
  LIVE_STREAM_LIMIT(
      HttpStatus.TOO_MANY_REQUESTS, "too many live streams; refresh instead and retry in %s s");

  private final HttpStatus status;
  private final String template;

  LiveErrorCode(HttpStatus status, String template) {
    this.status = status;
    this.template = template;
  }

  public HttpStatus status() {
    return status;
  }

  public String format(Object... args) {
    return args == null || args.length == 0 ? template : template.formatted(args);
  }
}
