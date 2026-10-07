package com.example.short_link.portability.domain;

import java.util.Locale;
import java.util.Optional;

// The files Mastodon's import takes, by the same names as the export's.
public enum ImportKind {
  FOLLOWING("Account address"),
  BLOCKS(null),
  MUTES("Account address"),
  DOMAIN_BLOCKS(null),
  BOOKMARKS(null),
  LISTS(null);

  private final String header;

  ImportKind(String header) {
    this.header = header;
  }

  // The first cell of the header row the file may start with, or null when it has none.
  public String header() {
    return header;
  }

  public static Optional<ImportKind> parse(String name) {
    if (name == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(valueOf(name.trim().replace('-', '_').toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
