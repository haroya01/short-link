package com.example.short_link.note.domain;

import java.util.Locale;
import java.util.Optional;

// Set on a thread's first note. FOLLOWING is the people the writer follows; MENTIONED is the
// accounts the first note names, who may also answer under FOLLOWING. The writer may always
// answer in their own thread.
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
