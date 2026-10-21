package com.example.short_link.common.post;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

// Who may read a published post. PUBLIC shows everywhere; UNLISTED only to whoever holds its key;
// FOLLOWERS and PASSWORD show everyone a locked card and their readers the post; a room post is for
// its room's members alone and never leaves the room, not even as a card or an announcement. Every
// decision about a post's audience is one of the questions below, and PostAccessSql asks the same
// ones in SQL: a new audience answers each here, and a new kind of reader is a new Reader.
public enum PostAudience {
  PUBLIC,
  UNLISTED,
  FOLLOWERS,
  PASSWORD,
  ROOM;

  public static final PostAudience DEFAULT = PUBLIC;

  public enum Reader {
    ANYONE,
    KEY_HOLDER,
    FOLLOWER,
    GRANT_HOLDER,
    MEMBER
  }

  public boolean readBy(Reader reader) {
    return switch (reader) {
      case ANYONE ->
          switch (this) {
            case PUBLIC -> true;
            case UNLISTED, FOLLOWERS, PASSWORD, ROOM -> false;
          };
      case KEY_HOLDER ->
          switch (this) {
            case PUBLIC, UNLISTED -> true;
            case FOLLOWERS, PASSWORD, ROOM -> false;
          };
      case FOLLOWER ->
          switch (this) {
            case PUBLIC, FOLLOWERS -> true;
            case UNLISTED, PASSWORD, ROOM -> false;
          };
      case GRANT_HOLDER ->
          switch (this) {
            case PUBLIC, PASSWORD -> true;
            case UNLISTED, FOLLOWERS, ROOM -> false;
          };
      case MEMBER ->
          switch (this) {
            case ROOM -> true;
            case PUBLIC, UNLISTED, FOLLOWERS, PASSWORD -> false;
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

  public boolean listed() {
    return readBy(Reader.ANYONE);
  }

  public boolean shownAsCard() {
    return switch (this) {
      case PUBLIC, FOLLOWERS, PASSWORD -> true;
      case UNLISTED, ROOM -> false;
    };
  }

  public boolean onProfile(boolean author) {
    return shownAsCard() || (author && keyed());
  }

  public boolean keyed() {
    return readBy(Reader.KEY_HOLDER) && !listed();
  }

  public boolean needsPassword() {
    return readBy(Reader.GRANT_HOLDER) && !listed();
  }

  public boolean inRoom() {
    return readBy(Reader.MEMBER);
  }

  public PostLock lock() {
    return switch (this) {
      case FOLLOWERS -> PostLock.FOLLOWERS;
      case PASSWORD -> PostLock.PASSWORD;
      case PUBLIC, UNLISTED, ROOM -> null;
    };
  }

  public static List<PostAudience> where(Predicate<PostAudience> question) {
    return Arrays.stream(values()).filter(question).toList();
  }

  public static Optional<PostAudience> parse(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    String wanted = value.strip().toUpperCase(Locale.ROOT);
    return Arrays.stream(values()).filter(audience -> audience.name().equals(wanted)).findFirst();
  }
}
