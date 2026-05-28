package com.example.short_link.link.domain;

enum OgFetchStatus {
  PENDING("PENDING"),
  OK("OK"),
  RETRYABLE("RETRYABLE"),
  ERROR("ERROR");

  private final String value;

  OgFetchStatus(String value) {
    this.value = value;
  }

  String value() {
    return value;
  }

  static OgFetchStatus failure(boolean willRetry) {
    return willRetry ? RETRYABLE : ERROR;
  }
}
