package com.example.short_link.home.application.read;

import com.example.short_link.post.application.read.BlogSlice;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

// Clients hand these back as they got them, so every shape once given out must keep reading.
sealed interface HomeCursor {

  // "<notes>.<posts>", the next position in each source of following, latest, trending and the
  // rooms; "h<asOf ms>.<notes>.<posts>" the same in the list without what was seen before asOf.
  record Positions(Instant asOf, int notes, int posts) implements HomeCursor {

    static final Positions START = new Positions(null, 0, 0);

    // A cursor from a scroll that left out the seen keeps doing so; one from before it never does.
    static Positions read(String raw, boolean hideSeen, Instant now) {
      if (raw == null) {
        return new Positions(hideSeen ? now : null, 0, 0);
      }
      if (!raw.startsWith("h")) {
        return plain(raw);
      }
      String[] parts = raw.substring(1).split("\\.");
      try {
        if (parts.length == 3) {
          long asOf = Long.parseLong(parts[0]);
          int notes = Integer.parseInt(parts[1]);
          int posts = Integer.parseInt(parts[2]);
          if (asOf >= 0 && notes >= 0 && posts >= 0) {
            return new Positions(Instant.ofEpochMilli(asOf), notes, posts);
          }
        }
      } catch (NumberFormatException malformed) {
        return new Positions(now, 0, 0);
      }
      return new Positions(now, 0, 0);
    }

    static Positions plain(String raw) {
      if (raw == null) {
        return START;
      }
      String[] parts = raw.split("\\.");
      try {
        if (parts.length == 2) {
          return new Positions(
              null,
              Math.max(Integer.parseInt(parts[0]), 0),
              Math.max(Integer.parseInt(parts[1]), 0));
        }
      } catch (NumberFormatException malformed) {
        return START;
      }
      return START;
    }

    Positions next(int notes, int posts) {
      return new Positions(asOf, notes, posts);
    }

    @Override
    public String toString() {
      return (asOf == null ? "" : "h" + asOf.toEpochMilli() + ".") + notes + "." + posts;
    }
  }

  // "r<asOf ms>.<offset>", a place in the for-you ranking made at asOf; "rh…" one that leaves out
  // the seen. A cursor goes on hiding the seen, or not, as its first page did.
  record Ranked(Instant asOf, int offset, boolean hide) implements HomeCursor {

    static Ranked parse(String raw) {
      if (raw == null || !raw.startsWith("r")) {
        return null;
      }
      boolean hide = raw.startsWith("rh");
      int start = hide ? 2 : 1;
      int dot = raw.indexOf('.');
      if (dot <= start) {
        return null;
      }
      try {
        long asOf = Long.parseLong(raw.substring(start, dot));
        int offset = Integer.parseInt(raw.substring(dot + 1));
        return asOf >= 0 && offset >= 0
            ? new Ranked(Instant.ofEpochMilli(asOf), offset, hide)
            : null;
      } catch (NumberFormatException malformed) {
        return null;
      }
    }

    @Override
    public String toString() {
      return (hide ? "rh" : "r") + asOf.toEpochMilli() + "." + offset;
    }
  }

  // "b<published µs>.<id>", the place of a blog post, both the blog's cursor and its since marker.
  record Blog(Instant publishedAt, long id, String language, boolean rest) implements HomeCursor {

    private static final String IN_THE_LANGUAGE = "1";
    private static final String AMONG_THE_REST = "2";
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,8}");

    static Blog of(BlogSlice.Place place, String language) {
      return place == null
          ? null
          : new Blog(place.publishedAt(), place.id(), language, place.rest());
    }

    static Blog newest(BlogSlice.Place place, String language) {
      return place == null
          ? null
          : new Blog(place.publishedAt(), place.id(), place.rest() ? null : language, false);
    }

    static String language(String tag) {
      return tag != null && LANGUAGE.matcher(tag).matches() ? tag : null;
    }

    static Blog parse(String raw) {
      if (raw == null || !raw.startsWith("b")) {
        return null;
      }
      String[] parts = raw.substring(1).split("\\.");
      try {
        return switch (parts.length) {
          case 2 -> at(parts[0], parts[1], null, false);
          case 3 -> among(parts[0], parts[1], parts[2]);
          default -> null;
        };
      } catch (NumberFormatException | ArithmeticException | DateTimeException malformed) {
        return null;
      }
    }

    private static Blog among(String head, String rawMicros, String rawId) {
      String[] named = head.split("-", -1);
      if (named.length != 2 || language(named[1]) == null) {
        return null;
      }
      boolean rest = named[0].equals(AMONG_THE_REST);
      return rest || named[0].equals(IN_THE_LANGUAGE) ? at(rawMicros, rawId, named[1], rest) : null;
    }

    private static Blog at(String rawMicros, String rawId, String language, boolean rest) {
      long micros = Long.parseLong(rawMicros);
      long id = Long.parseLong(rawId);
      return micros >= 0 && id > 0
          ? new Blog(Instant.EPOCH.plus(micros, ChronoUnit.MICROS), id, language, rest)
          : null;
    }

    @Override
    public String toString() {
      String place = ChronoUnit.MICROS.between(Instant.EPOCH, publishedAt) + "." + id;
      if (language == null) {
        return "b" + place;
      }
      return "b" + (rest ? AMONG_THE_REST : IN_THE_LANGUAGE) + "-" + language + "." + place;
    }
  }
}
