package com.example.short_link.federation.application.trends;

import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.common.seen.SeenViewer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Pages through what other servers trend on, then, for a member, on through the notes from
// elsewhere this server keeps, newest first. A cursor is t<offset> in the first part and r<id>
// (the last note shown) in the second.
//
// The latest are what the chosen servers posted last. Once pulling is on they are the notes relays
// brought and pulls kept, newest first, for anyone: a cursor is r<id> and since is the id of the
// newest note the reader has. Until then they are read from memory, a cursor is t<offset> and
// since is a post's uri.
@Service
@RequiredArgsConstructor
public class RemoteTrendingFeed {

  private final TrendingPosts trending;
  private final LatestPosts latest;
  private final LatestProperties latestProps;
  private final TrendingAuthors authors;
  private final NoteViewReader notes;
  private final RemoteSeen seen;
  private final Clock clock;

  static final int MAX_READS = 5;

  public record Page(
      List<Object> items,
      String next,
      @JsonInclude(JsonInclude.Include.NON_NULL) Boolean gap,
      @JsonInclude(JsonInclude.Include.NON_NULL) Integer seenFrom) {

    public Page(List<Object> items, String next, Boolean gap) {
      this(items, next, gap, null);
    }

    public Page(List<Object> items, String next) {
      this(items, next, null, null);
    }
  }

  public record KeptNote(@JsonUnwrapped Object note, String uri) {}

  private record Candidate(Object item, String uri, String after) {}

  private record Below(List<Candidate> items, boolean more) {}

  private enum Phase {
    UNSEEN('u'),
    UNSEEN_PAST_SEEN('v'),
    SEEN_AHEAD('d'),
    SEEN('s');

    final char mark;

    Phase(char mark) {
      this.mark = mark;
    }

    boolean unseen() {
      return this == UNSEEN || this == UNSEEN_PAST_SEEN;
    }
  }

  public Page page(Long viewerId, String cursor, int limit) {
    int size = Math.clamp(limit, 1, TrendingPosts.MAX_LIMIT);
    if (cursor != null && cursor.startsWith("r")) {
      return viewerId == null ? new Page(List.of(), null) : kept(viewerId, id(cursor), size);
    }
    TrendingPosts.Page pool = trending.page(viewerId, offset(cursor), size);
    if (viewerId != null && !pool.unknownAuthors().isEmpty()) {
      authors.warm(pool.unknownAuthors());
    }
    String next =
        pool.more() ? "t" + (offset(cursor) + pool.items().size()) : viewerId == null ? null : "r";
    return new Page(List.copyOf(pool.items()), next);
  }

  public Page latest(Long viewerId, SeenViewer viewer, String cursor, String since, int limit) {
    int size = Math.clamp(limit, 1, TrendingPosts.MAX_LIMIT);
    if (viewer != null && unseenFirstAsked(cursor, since)) {
      return unseenFirst(viewerId, viewer, cursor, size);
    }
    if (latestProps.pulling()) {
      return stored(viewerId, cursor, since, size);
    }
    boolean fresh = since != null && cursor == null;
    if (fresh) {
      Optional<Page> newer = newerThanHeld(viewerId, since);
      if (newer.isPresent()) {
        return newer.get();
      }
    }
    LatestPosts.Page page = latest.page(viewerId, offset(cursor), size);
    warm(viewerId, page.unknownAuthors());
    return new Page(
        List.copyOf(page.items()),
        page.more() ? "t" + (offset(cursor) + page.items().size()) : null,
        fresh ? Boolean.TRUE : null);
  }

  private static boolean unseenFirstAsked(String cursor, String since) {
    return cursor == null ? since == null : seenCursor(cursor);
  }

  private Optional<Page> newerThanHeld(Long viewerId, String since) {
    LatestPosts.Page top = latest.page(viewerId, 0, TrendingPosts.MAX_LIMIT);
    List<String> uris = top.items().stream().map(TrendingNote::uri).toList();
    int at = uris.indexOf(since);
    if (at < 0) {
      return Optional.empty();
    }
    warm(viewerId, top.unknownAuthors());
    return Optional.of(new Page(List.copyOf(top.items().subList(0, at)), null, false));
  }

  private void warm(Long viewerId, List<String> unknownAuthors) {
    if (viewerId != null && !unknownAuthors.isEmpty()) {
      authors.warm(unknownAuthors);
    }
  }

  private Page stored(Long viewerId, String cursor, String since, int size) {
    boolean fresh = since != null && cursor == null;
    if (fresh) {
      List<NoteViewReader.Listed> top = notes.latest(viewerId, null, TrendingPosts.MAX_LIMIT);
      for (int at = 0; at < top.size(); at++) {
        if (String.valueOf(top.get(at).id()).equals(since)) {
          List<Object> items = new ArrayList<>(at);
          top.subList(0, at).forEach(row -> items.add(new KeptNote(row.view(), row.uri())));
          return new Page(items, null, false);
        }
      }
    }
    Long beforeId = cursor != null && cursor.startsWith("r") ? id(cursor) : null;
    List<NoteViewReader.Listed> rows = notes.latest(viewerId, beforeId, size + 1);
    boolean more = rows.size() > size;
    List<NoteViewReader.Listed> shown = more ? rows.subList(0, size) : rows;
    List<Object> items = new ArrayList<>(shown.size());
    shown.forEach(row -> items.add(new KeptNote(row.view(), row.uri())));
    return new Page(items, more ? "r" + shown.getLast().id() : null, fresh ? Boolean.TRUE : null);
  }

  private Page unseenFirst(Long viewerId, SeenViewer viewer, String cursor, int size) {
    Walk walk = Walk.from(cursor, clock.instant().truncatedTo(ChronoUnit.MILLIS), size);
    for (int reads = 0; reads < MAX_READS && walk.items.size() < size; reads++) {
      Below below = below(viewerId, walk.at, size);
      Set<String> seenBefore =
          seen.seenBefore(viewer, below.items().stream().map(Candidate::uri).toList(), walk.asOf);
      boolean full = walk.takeAll(below.items(), seenBefore);
      if (full || below.more()) {
        continue;
      }
      if (walk.phase != Phase.UNSEEN_PAST_SEEN) {
        return new Page(walk.items, null, null, walk.seenFrom);
      }
      walk.phase = Phase.SEEN_AHEAD;
      walk.at = "";
    }
    return new Page(
        walk.items,
        walk.phase.mark + String.valueOf(walk.asOf.toEpochMilli()) + "." + walk.at,
        null,
        walk.seenFrom);
  }

  private static final class Walk {

    private final int size;
    private final Instant asOf;
    private final List<Object> items;
    private Phase phase;
    private String at;
    private Integer seenFrom;

    private Walk(int size, Instant asOf, Phase phase, String at) {
      this.size = size;
      this.asOf = asOf;
      this.items = new ArrayList<>(size);
      this.phase = phase;
      this.at = at;
    }

    static Walk from(String cursor, Instant now, int size) {
      if (cursor == null) {
        return new Walk(size, now, Phase.UNSEEN, "");
      }
      int dot = cursor.indexOf('.');
      return new Walk(
          size,
          Instant.ofEpochMilli(Long.parseLong(cursor.substring(1, dot))),
          phaseOf(cursor.charAt(0)),
          cursor.substring(dot + 1));
    }

    boolean takeAll(List<Candidate> candidates, Set<String> seenBefore) {
      for (Candidate candidate : candidates) {
        if (items.size() == size) {
          return true;
        }
        take(candidate, seenBefore.contains(candidate.uri()));
        at = candidate.after();
      }
      return false;
    }

    private void take(Candidate candidate, boolean wasSeen) {
      if (phase.unseen() && wasSeen) {
        phase = Phase.UNSEEN_PAST_SEEN;
      } else if (phase.unseen() || wasSeen) {
        if (phase == Phase.SEEN_AHEAD) {
          seenFrom = items.size();
          phase = Phase.SEEN;
        }
        items.add(candidate.item());
      }
    }
  }

  private Below below(Long viewerId, String at, int size) {
    if (latestProps.pulling()) {
      Long beforeId = at.startsWith("r") ? id(at) : null;
      List<NoteViewReader.Listed> rows = notes.latest(viewerId, beforeId, size + 1);
      boolean more = rows.size() > size;
      List<Candidate> items = new ArrayList<>(size);
      for (NoteViewReader.Listed row : more ? rows.subList(0, size) : rows) {
        items.add(new Candidate(new KeptNote(row.view(), row.uri()), row.uri(), "r" + row.id()));
      }
      return new Below(items, more);
    }
    int from = offset(at);
    LatestPosts.Page page = latest.page(viewerId, from, size);
    warm(viewerId, page.unknownAuthors());
    List<Candidate> items = new ArrayList<>(page.items().size());
    for (int i = 0; i < page.items().size(); i++) {
      TrendingNote note = page.items().get(i);
      items.add(new Candidate(note, note.uri(), "t" + (from + i + 1)));
    }
    return new Below(items, page.more());
  }

  private static boolean seenCursor(String cursor) {
    if (cursor.length() < 3 || phaseOf(cursor.charAt(0)) == null) {
      return false;
    }
    int dot = cursor.indexOf('.');
    if (dot < 2) {
      return false;
    }
    try {
      return Long.parseLong(cursor.substring(1, dot)) >= 0;
    } catch (NumberFormatException malformed) {
      return false;
    }
  }

  private static Phase phaseOf(char mark) {
    for (Phase phase : Phase.values()) {
      if (phase.mark == mark) {
        return phase;
      }
    }
    return null;
  }

  private Page kept(Long viewerId, Long beforeId, int size) {
    return paged(notes.received(viewerId, beforeId, size + 1), size);
  }

  private static Page paged(List<NoteViewReader.Listed> rows, int size) {
    boolean more = rows.size() > size;
    List<NoteViewReader.Listed> shown = more ? rows.subList(0, size) : rows;
    return new Page(views(shown), more ? "r" + shown.getLast().id() : null);
  }

  private static List<Object> views(List<NoteViewReader.Listed> rows) {
    List<Object> items = new ArrayList<>(rows.size());
    rows.forEach(row -> items.add(row.view()));
    return items;
  }

  private static int offset(String cursor) {
    if (cursor == null || !cursor.startsWith("t")) {
      return 0;
    }
    try {
      return Math.max(Integer.parseInt(cursor.substring(1)), 0);
    } catch (NumberFormatException malformed) {
      return 0;
    }
  }

  private static Long id(String cursor) {
    try {
      return cursor.length() == 1 ? null : Long.parseLong(cursor.substring(1));
    } catch (NumberFormatException malformed) {
      return null;
    }
  }
}
