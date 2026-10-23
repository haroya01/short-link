package com.example.short_link.home.application.read;

import com.example.short_link.common.seen.Unseen;
import com.example.short_link.federation.application.trends.LocalSeen;
import com.example.short_link.federation.domain.SeenItem;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteSlice;
import com.example.short_link.post.application.read.BlogSlice;
import com.example.short_link.post.application.read.PostSlice;
import com.example.short_link.post.application.read.PublicFeedQueryService;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// One feed of notes and blog posts. Following and latest run newest first across both by a note's
// writing time and a post's publishing time (a repost stands at its note's time); trending takes
// the two trending orders in turn. The cursor is the next position in each source,
// "<notes>.<posts>". Latest can leave out what the reader already had on screen: its cursor is then
// "h<asOf>.<notes>.<posts>", positions in the list without what was seen before asOf. For you is
// one ranked list of notes, posts and posts from elsewhere, which HomeForYou makes. Blog is the
// posts alone, newest published first at any age; its cursor and since marker are the position
// "b<published micros>.<id>".
@Service
@RequiredArgsConstructor
public class HomeFeedQueryService {

  static final int MAX_LIMIT = 40;
  static final int SINCE_LIMIT = 50;

  private final NoteQueryService notes;
  private final PublicFeedQueryService posts;
  private final LocalSeen seen;
  private final HomeForYou forYou;
  private final Clock clock;

  public enum Tab {
    FOLLOWING,
    LATEST,
    TRENDING,
    FOR_YOU,
    BLOG
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Item(String kind, Object note, Object post, Object remote) {}

  public record Page(
      List<Item> items,
      String next,
      @JsonInclude(JsonInclude.Include.NON_NULL) String newest,
      @JsonInclude(JsonInclude.Include.NON_NULL) Boolean gap) {

    Page(List<Item> items, String next) {
      this(items, next, null, null);
    }
  }

  record Merged(List<Item> items, int notesTaken, int postsTaken) {}

  record At(Instant asOf, int notes, int posts) {}

  Page page(
      Long viewerId,
      SeenViewer viewer,
      String tab,
      String cursor,
      String since,
      int limit,
      boolean hideSeen) {
    return page(viewerId, viewer, null, tab, cursor, since, limit, hideSeen);
  }

  public Page page(
      Long viewerId,
      SeenViewer viewer,
      String languages,
      String tab,
      String cursor,
      String since,
      int limit,
      boolean hideSeen) {
    Tab chosen = tab(tab);
    if (chosen == Tab.FOLLOWING && viewerId == null) {
      return new Page(List.of(), null);
    }
    int size = Math.clamp(limit, 1, MAX_LIMIT);
    if (chosen == Tab.FOR_YOU) {
      return forYou.page(viewerId, viewer, languages, cursor, size, hideSeen);
    }
    if (chosen == Tab.BLOG) {
      return blog(viewerId, cursor, since, size);
    }
    boolean hiding = chosen == Tab.LATEST && viewer != null;
    if (since != null && cursor == null) {
      return newer(viewerId, hiding && hideSeen ? viewer : null, chosen, since, size);
    }
    At at = at(cursor, hiding && hideSeen, now());
    Unseen unseen = hiding && at.asOf() != null ? seen.unseen(viewer, at.asOf()) : null;
    NoteSlice noteSlice = notes(chosen, viewerId, at.notes(), size + 1, unseen);
    PostSlice postSlice = posts(chosen, viewerId, at.posts(), size + 1, unseen);
    Merged merged = merge(chosen, noteSlice.items(), postSlice.items(), size);
    int nextNote =
        next(
            at.notes(),
            noteSlice.items().stream().map(NoteSlice.At::position).toList(),
            merged.notesTaken(),
            size,
            noteSlice.exhausted());
    int nextPost =
        next(
            at.posts(),
            postSlice.items().stream().map(PostSlice.At::position).toList(),
            merged.postsTaken(),
            size,
            postSlice.exhausted());
    boolean more =
        merged.notesTaken() < noteSlice.items().size()
            || merged.postsTaken() < postSlice.items().size()
            || !noteSlice.exhausted()
            || !postSlice.exhausted();
    String prefix = at.asOf() == null ? "" : "h" + at.asOf().toEpochMilli() + ".";
    return new Page(
        merged.items(),
        more ? prefix + nextNote + "." + nextPost : null,
        cursor == null ? top(noteSlice, postSlice, "0.0") : null,
        null);
  }

  record Marker(Instant publishedAt, long id) {

    static Marker of(Instant publishedAt, Long id) {
      return publishedAt == null ? null : new Marker(publishedAt, id);
    }

    static Marker parse(String raw) {
      if (raw == null || !raw.startsWith("b")) {
        return null;
      }
      String[] parts = raw.substring(1).split("\\.");
      try {
        if (parts.length == 2) {
          long micros = Long.parseLong(parts[0]);
          long id = Long.parseLong(parts[1]);
          if (micros >= 0 && id > 0) {
            return new Marker(Instant.EPOCH.plus(micros, ChronoUnit.MICROS), id);
          }
        }
      } catch (NumberFormatException | ArithmeticException | DateTimeException malformed) {
        return null;
      }
      return null;
    }

    @Override
    public String toString() {
      return "b" + ChronoUnit.MICROS.between(Instant.EPOCH, publishedAt) + "." + id;
    }
  }

  // Since brings what was published above the marker, at most SINCE_LIMIT; more than that is a gap
  // and a fresh first page.
  private Page blog(Long viewerId, String cursor, String since, int size) {
    if (since != null && cursor == null) {
      Marker marker = Marker.parse(since);
      BlogSlice above =
          marker == null
              ? null
              : posts.blogAbove(viewerId, marker.publishedAt(), marker.id(), SINCE_LIMIT);
      if (above != null && !above.more()) {
        Marker top = Marker.of(above.firstAt(), above.firstId());
        return new Page(cards(above), null, top == null ? since : top.toString(), false);
      }
      Page first = blog(viewerId, null, null, size);
      return new Page(first.items(), first.next(), first.newest(), true);
    }
    Marker from = Marker.parse(cursor);
    BlogSlice below =
        posts.blogBelow(
            viewerId,
            from == null ? null : from.publishedAt(),
            from == null ? null : from.id(),
            size);
    Marker last = Marker.of(below.lastAt(), below.lastId());
    Marker top = Marker.of(below.firstAt(), below.firstId());
    return new Page(
        cards(below),
        below.more() ? last.toString() : null,
        from == null && top != null ? top.toString() : null,
        null);
  }

  private static List<Item> cards(BlogSlice slice) {
    return slice.posts().stream().map(card -> new Item("post", null, card, null)).toList();
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MILLIS);
  }

  // What came in above the newest note and post the reader already has, at most SINCE_LIMIT. A
  // marker no longer within reach (deleted, or more arrived than fit) is a gap: the reader gets a
  // fresh first page to replace the list with. Trending has no "above", so it always starts over.
  // With a viewer to hide for, the marker is looked for in the list as it is, since the reader saw
  // it, and only what came above it leaves out the seen.
  private Page newer(Long viewerId, SeenViewer hideFor, Tab chosen, String since, int size) {
    long[] marker = markers(since);
    if (chosen == Tab.TRENDING || marker == null) {
      return fresh(viewerId, hideFor, chosen, size);
    }
    NoteSlice noteSlice = notes(chosen, viewerId, 0, SINCE_LIMIT + 1, null);
    PostSlice postSlice = posts(chosen, viewerId, 0, SINCE_LIMIT + 1, null);
    int newNotes = above(noteSlice.items().stream().map(at -> at.note().id()).toList(), marker[0]);
    int newPosts =
        above(postSlice.items().stream().map(at -> at.post().item().id()).toList(), marker[1]);
    if ((newNotes < 0 && !(marker[0] == 0 && noteSlice.exhausted()))
        || (newPosts < 0 && !(marker[1] == 0 && postSlice.exhausted()))) {
      return fresh(viewerId, hideFor, chosen, size);
    }
    List<NoteSlice.At> notesAbove =
        noteSlice.items().subList(0, newNotes < 0 ? noteSlice.items().size() : newNotes);
    List<PostSlice.At> postsAbove =
        postSlice.items().subList(0, newPosts < 0 ? postSlice.items().size() : newPosts);
    if (notesAbove.size() + postsAbove.size() > SINCE_LIMIT) {
      return fresh(viewerId, hideFor, chosen, size);
    }
    if (hideFor != null && notesAbove.size() + postsAbove.size() > 0) {
      List<SeenItem> asked = new ArrayList<>();
      notesAbove.forEach(at -> asked.add(new SeenItem(SeenItem.NOTE, at.note().id())));
      postsAbove.forEach(at -> asked.add(new SeenItem(SeenItem.POST, at.post().item().id())));
      Set<SeenItem> hidden = seen.seenBefore(hideFor, asked, now());
      notesAbove =
          notesAbove.stream()
              .filter(
                  at ->
                      Objects.equals(at.note().author().id(), viewerId)
                          || !hidden.contains(new SeenItem(SeenItem.NOTE, at.note().id())))
              .toList();
      postsAbove =
          postsAbove.stream()
              .filter(
                  at ->
                      Objects.equals(at.post().item().author().id(), viewerId)
                          || !hidden.contains(new SeenItem(SeenItem.POST, at.post().item().id())))
              .toList();
    }
    Merged merged = merge(chosen, notesAbove, postsAbove, SINCE_LIMIT);
    return new Page(merged.items(), null, top(noteSlice, postSlice, since), false);
  }

  private Page fresh(Long viewerId, SeenViewer hideFor, Tab chosen, int size) {
    Page first = page(viewerId, hideFor, chosen.name(), null, null, size, hideFor != null);
    return new Page(first.items(), first.next(), first.newest(), true);
  }

  // The index of the marker in the list, which is how many came above it; -1 when it is not there.
  private static int above(List<Long> ids, long marker) {
    if (marker == 0) {
      return -1;
    }
    return ids.indexOf(marker);
  }

  private static String top(NoteSlice notes, PostSlice posts, String before) {
    long[] previous = markers(before);
    long note =
        notes.items().isEmpty()
            ? (previous == null ? 0 : previous[0])
            : notes.items().getFirst().note().id();
    long post =
        posts.items().isEmpty()
            ? (previous == null ? 0 : previous[1])
            : posts.items().getFirst().post().item().id();
    return note + "." + post;
  }

  private static long[] markers(String since) {
    String[] parts = since.split("\\.");
    try {
      return parts.length == 2
          ? new long[] {Long.parseLong(parts[0]), Long.parseLong(parts[1])}
          : null;
    } catch (NumberFormatException malformed) {
      return null;
    }
  }

  private NoteSlice notes(Tab chosen, Long viewerId, int offset, int limit, Unseen unseen) {
    return switch (chosen) {
      case FOLLOWING -> notes.followingAt(viewerId, offset, limit);
      case LATEST, FOR_YOU, BLOG -> notes.everyoneAt(viewerId, offset, limit, unseen);
      case TRENDING -> notes.trendingAt(viewerId, offset, limit);
    };
  }

  private PostSlice posts(Tab chosen, Long viewerId, int offset, int limit, Unseen unseen) {
    return switch (chosen) {
      case FOLLOWING -> posts.followingAt(viewerId, offset, limit);
      case LATEST, FOR_YOU, BLOG -> posts.recentAt(viewerId, offset, limit, unseen);
      case TRENDING -> posts.trendingAt(viewerId, offset, limit);
    };
  }

  static Merged merge(
      Tab chosen, List<NoteSlice.At> noteQueue, List<PostSlice.At> postQueue, int size) {
    List<Item> items = new ArrayList<>(size);
    int n = 0;
    int p = 0;
    boolean noteTurn = true;
    while (items.size() < size && (n < noteQueue.size() || p < postQueue.size())) {
      boolean takeNote;
      if (n >= noteQueue.size()) {
        takeNote = false;
      } else if (p >= postQueue.size()) {
        takeNote = true;
      } else if (chosen == Tab.TRENDING) {
        takeNote = noteTurn;
      } else {
        takeNote =
            !noteQueue
                .get(n)
                .note()
                .createdAt()
                .isBefore(postQueue.get(p).post().item().publishedAt());
      }
      if (takeNote) {
        items.add(new Item("note", noteQueue.get(n++).note(), null, null));
      } else {
        items.add(new Item("post", null, postQueue.get(p++).post(), null));
      }
      noteTurn = !takeNote;
    }
    return new Merged(items, n, p);
  }

  static int next(int from, List<Integer> positions, int taken, int size, boolean exhausted) {
    if (taken > 0) {
      return positions.get(taken - 1) + 1;
    }
    return positions.isEmpty() && !exhausted ? from + size + 1 : from;
  }

  private static Tab tab(String raw) {
    if (raw == null) {
      return Tab.LATEST;
    }
    try {
      return Tab.valueOf(raw.strip().replace('-', '_').toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      return Tab.LATEST;
    }
  }

  // A cursor from a scroll that left out the seen keeps doing so; one from before it never does.
  static At at(String cursor, boolean hideSeen, Instant now) {
    if (cursor == null) {
      return new At(hideSeen ? now : null, 0, 0);
    }
    if (!cursor.startsWith("h")) {
      int[] at = positions(cursor);
      return new At(null, at[0], at[1]);
    }
    String[] parts = cursor.substring(1).split("\\.");
    try {
      if (parts.length == 3) {
        long asOf = Long.parseLong(parts[0]);
        int notes = Integer.parseInt(parts[1]);
        int posts = Integer.parseInt(parts[2]);
        if (asOf >= 0 && notes >= 0 && posts >= 0) {
          return new At(Instant.ofEpochMilli(asOf), notes, posts);
        }
      }
    } catch (NumberFormatException malformed) {
      return new At(now, 0, 0);
    }
    return new At(now, 0, 0);
  }

  static int[] positions(String cursor) {
    if (cursor == null) {
      return new int[] {0, 0};
    }
    String[] parts = cursor.split("\\.");
    try {
      if (parts.length == 2) {
        return new int[] {
          Math.max(Integer.parseInt(parts[0]), 0), Math.max(Integer.parseInt(parts[1]), 0)
        };
      }
    } catch (NumberFormatException malformed) {
      return new int[] {0, 0};
    }
    return new int[] {0, 0};
  }
}
