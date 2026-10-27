package com.example.short_link.note.domain;

import java.util.Locale;
import java.util.Optional;

public enum NoteReplyPolicy {
  EVERYONE,
  FOLLOWING,
  MENTIONED;

  public boolean limited() {
    return this != EVERYONE;
  }

  public String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Optional<NoteReplyPolicy> parse(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    for (NoteReplyPolicy policy : values()) {
      if (policy.apiName().equals(value.strip().toLowerCase(Locale.ROOT))) {
        return Optional.of(policy);
      }
    }
    return Optional.empty();
  }
}
