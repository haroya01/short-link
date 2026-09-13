package com.example.short_link.link.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Keep validation aligned with the redirect path regex and {@code SecurityConfig.SHORT_CODE_REGEX}.
 * JSON remains a bare string; JPA stores VARCHAR through {@code ShortCodeAttributeConverter}.
 */
public record ShortCode(@JsonValue String value) {

  public ShortCode {
    if (value == null || !value.matches("^[0-9A-Za-z]{3,16}$")) {
      throw new IllegalArgumentException("short code must be 3..16 alphanumeric: " + value);
    }
  }

  @JsonCreator
  public static ShortCode of(String value) {
    return new ShortCode(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
