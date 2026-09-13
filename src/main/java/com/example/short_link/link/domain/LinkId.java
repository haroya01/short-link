package com.example.short_link.link.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Application IDs use this type; JPA identity generation and persistence-context keys remain {@code
 * Long}. Convert with {@link #of} and {@link #value} at the repository boundary. JSON remains a
 * bare number.
 */
public record LinkId(@JsonValue Long value) {

  public LinkId {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException("link id must be positive: " + value);
    }
  }

  @JsonCreator
  public static LinkId of(Long value) {
    return new LinkId(value);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
