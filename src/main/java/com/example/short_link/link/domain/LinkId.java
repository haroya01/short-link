package com.example.short_link.link.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Strongly-typed wrapper for a {@code link.id} primary key value. Sits at the application / service
 * / DTO layer so a {@code Long userId} can't be silently passed where a {@code Long linkId} was
 * expected — the two would have compiled before, and the bug would only surface in production.
 *
 * <p>JPA primary-key columns are intentionally NOT migrated to {@code LinkId} (Hibernate identity
 * generation + the persistence-context cache stay on raw {@code Long}). Cross the boundary with
 * {@link LinkId#of} when handing a generated id to the application layer and {@link #value} when
 * going back to a repository.
 *
 * <p>JSON serializes to / from a bare number via {@link JsonValue} / {@link JsonCreator}.
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
