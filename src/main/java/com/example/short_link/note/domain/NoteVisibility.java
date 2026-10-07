package com.example.short_link.note.domain;

import java.util.Locale;
import java.util.Optional;

// Mastodon's four: public shows everywhere; unlisted is public but stays out of the shared feeds
// (everyone, trending, tags); private is for followers and mentioned members; direct is for
// mentioned members only and never leaves kurl.
public enum NoteVisibility {
  PUBLIC,
  UNLISTED,
  PRIVATE,
  DIRECT;

  public boolean shareable() {
    return this == PUBLIC || this == UNLISTED;
  }

  public boolean restricted() {
    return !shareable();
  }

  public String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Optional<NoteVisibility> parse(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    for (NoteVisibility visibility : values()) {
      if (visibility.apiName().equals(value.strip().toLowerCase(Locale.ROOT))) {
        return Optional.of(visibility);
      }
    }
    return Optional.empty();
  }
}
