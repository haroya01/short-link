package com.example.short_link.link.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Link short code — 3..16 alphanumeric characters. The constructor enforces the same shape the
 * {@code RedirectController} path regex and {@code SecurityConfig.SHORT_CODE_REGEX} use, so an
 * invalid short code can never be carried through the system as a valid identifier.
 *
 * <p>JSON serializes to / from a bare string via {@link JsonValue} / {@link JsonCreator}, so the
 * wire format is unchanged. JPA persists as VARCHAR through {@code ShortCodeAttributeConverter}
 * (autoApply).
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
