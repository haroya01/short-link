package com.example.short_link.common.note;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

// Mastodon's four: public shows everywhere; unlisted is public but stays out of the shared feeds
// (everyone, trending, tags); private is for followers and mentioned members; direct is for
// mentioned members only and never leaves kurl. Every decision about who sees a note is one of the
// questions below, and NoteAccessSql asks the same ones in SQL: a new boundary answers each here,
// and a new kind of reader is a new Reader that NoteAccessSql gives its condition.
public enum NoteVisibility {
  PUBLIC,
  UNLISTED,
  PRIVATE,
  DIRECT;

  public static final NoteVisibility DEFAULT = PUBLIC;

  public boolean listed() {
    return switch (this) {
      case PUBLIC -> true;
      case UNLISTED, PRIVATE, DIRECT -> false;
    };
  }

  public enum Reader {
    ANYONE,
    AUTHOR,
    NAMED,
    FOLLOWER
  }

  public boolean readBy(Reader reader) {
    return switch (reader) {
      case ANYONE ->
          switch (this) {
            case PUBLIC, UNLISTED -> true;
            case PRIVATE, DIRECT -> false;
          };
      case AUTHOR, NAMED ->
          switch (this) {
            case PUBLIC, UNLISTED, PRIVATE, DIRECT -> true;
          };
      case FOLLOWER ->
          switch (this) {
            case PUBLIC, UNLISTED, PRIVATE -> true;
            case DIRECT -> false;
          };
    };
  }

  public boolean readableBy(Predicate<Reader> holds) {
    for (Reader reader : Reader.values()) {
      if (readBy(reader) && holds.test(reader)) {
        return true;
      }
    }
    return false;
  }

  public boolean shareable() {
    return readBy(Reader.ANYONE);
  }

  public boolean followersRead() {
    return readBy(Reader.FOLLOWER);
  }

  public boolean direct() {
    return switch (this) {
      case DIRECT -> true;
      case PUBLIC, UNLISTED, PRIVATE -> false;
    };
  }

  public boolean restricted() {
    return !shareable();
  }

  public String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static List<NoteVisibility> where(Predicate<NoteVisibility> question) {
    return Arrays.stream(values()).filter(question).toList();
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

  public static NoteVisibility addressed(
      boolean toEveryone, boolean copiedToEveryone, boolean followers) {
    if (toEveryone) {
      return PUBLIC;
    }
    if (copiedToEveryone) {
      return UNLISTED;
    }
    return followers ? PRIVATE : DIRECT;
  }
}
