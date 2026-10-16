package com.example.short_link.post.application.access;

public record ReaderCountry(String code, boolean deferred) {

  static final ReaderCountry DEFERRED = new ReaderCountry(null, true);

  static ReaderCountry of(String code) {
    return new ReaderCountry(code, false);
  }
}
