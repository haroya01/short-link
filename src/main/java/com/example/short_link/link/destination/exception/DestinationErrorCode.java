package com.example.short_link.link.destination.exception;

import org.springframework.http.HttpStatus;

public enum DestinationErrorCode {
  DESTINATION_NOT_FOUND(HttpStatus.NOT_FOUND, "destination not found"),
  INVALID_DESTINATION_URL(HttpStatus.BAD_REQUEST, "destination url must be http(s)"),
  TOO_MANY_DESTINATIONS(
      HttpStatus.CONFLICT, "too many destinations for this link (max %d)", "limit");

  private final HttpStatus status;
  private final String template;
  private final String[] metadataKeys;

  DestinationErrorCode(HttpStatus status, String template, String... metadataKeys) {
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
