package com.example.short_link.home.application.read;

import com.example.short_link.common.seen.SeenItem;
import com.example.short_link.common.seen.SeenViewer;
import com.example.short_link.common.seen.Unseen;
import com.example.short_link.federation.application.trends.LocalSeen;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteSlice;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.post.application.read.BlogSlice;
import com.example.short_link.post.application.read.PostCard;
import com.example.short_link.post.application.read.PostHomeSlices;
import com.example.short_link.post.application.read.PostSlice;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Clock;
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
// the two trending orders in turn. Latest can leave out what the reader already had on screen. For
// you is one ranked list of notes, posts and posts from elsewhere, which HomeForYou makes. Blog is
// the posts alone, newest published first at any age.
@Service
@RequiredArgsConstructor
public class HomeFeedQueryService {

  static final int SINCE_LIMIT = 50;

  private final NoteQueryService notes;
  private final PostHomeSlices posts;
  private final LocalSeen seen;
  private final HomeForYou forYou;
  private final Clock clock;

  public enum Tab {
    FOLLOWING,
    LATEST,
    TRENDING,
    FOR_YOU,
    BLOG;

    public static Tab of(String raw) {
      if (raw == null) {
        return LATEST;
      }
      try {
        return valueOf(raw.strip().replace('-', '_').toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException unknown) {
        return LATEST;
      }
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Item(String kind, NoteView note, PostCard post, Object remote) {}

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

  public Page page(HomeAsk ask) {
    Tab chosen = ask.tab();
    Long viewerId = ask.viewerId();
    if (chosen == Tab.FOLLOWING && viewerId == null) {
      return new Page(List.of(), null);
    }
    int size = ask.size();
    if (chosen == Tab.FOR_YOU) {
      return forYou.page(ask);
    }
    if (chosen == Tab.BLOG) {
      return blog(viewerId, ask.cursor(), ask.since(), size);
    }
    SeenViewer viewer = ask.viewer();
    boolean hiding = chosen == Tab.LATEST && viewer != null;
    if (ask.since() != null && ask.cursor() == null) {
      return newer(ask, hiding && ask.hideSeen() ? viewer : null);
    }
    HomeCursor.Positions at =
        HomeCursor.Positions.read(ask.cursor(), hiding && ask.hideSeen(), now());
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
    return new Page(
        merged.items(),
        more ? at.next(nextNote, nextPost).toString() : null,
        ask.cursor() == null ? top(noteSlice, postSlice, SinceMarker.NONE) : null,
        null);
  }

  // Since brings what was published above the marker, at most SINCE_LIMIT; more than that is a gap
  // and a fresh first page.
  private Page blog(Long viewerId, String cursor, String since, int size) {
    if (since != null && cursor == null) {
      HomeCursor.Blog marker = HomeCursor.Blog.parse(since);
      BlogSlice above =
          marker == null
              ? null
              : posts.blogAbove(viewerId, marker.publishedAt(), marker.id(), SINCE_LIMIT);
      if (above != null && !above.more()) {
        HomeCursor.Blog top = HomeCursor.Blog.of(above.firstAt(), above.firstId());
        return new Page(cards(above), null, top == null ? since : top.toString(), false);
      }
      Page first = blog(viewerId, null, null, size);
      return new Page(first.items(), first.next(), first.newest(), true);
    }
    HomeCursor.Blog from = HomeCursor.Blog.parse(cursor);
    BlogSlice below =
        posts.blogBelow(
            viewerId,
            from == null ? null : from.publishedAt(),
            from == null ? null : from.id(),
            size);
    HomeCursor.Blog last = HomeCursor.Blog.of(below.lastAt(), below.lastId());
    HomeCursor.Blog top = HomeCursor.Blog.of(below.firstAt(), below.firstId());
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
  private Page newer(HomeAsk ask, SeenViewer hideFor) {
    Tab chosen = ask.tab();
    Long viewerId = ask.viewerId();
    SinceMarker marker = SinceMarker.parse(ask.since());
    if (chosen == Tab.TRENDING || marker == null) {
      return fresh(ask, hideFor);
    }
    NoteSlice noteSlice = notes(chosen, viewerId, 0, SINCE_LIMIT + 1, null);
    PostSlice postSlice = posts(chosen, viewerId, 0, SINCE_LIMIT + 1, null);
    int newNotes =
        above(noteSlice.items().stream().map(at -> at.note().id()).toList(), marker.note());
    int newPosts =
        above(postSlice.items().stream().map(at -> at.post().item().id()).toList(), marker.post());
    if ((newNotes < 0 && !(marker.note() == 0 && noteSlice.exhausted()))
        || (newPosts < 0 && !(marker.post() == 0 && postSlice.exhausted()))) {
      return fresh(ask, hideFor);
    }
    List<NoteSlice.At> notesAbove =
        noteSlice.items().subList(0, newNotes < 0 ? noteSlice.items().size() : newNotes);
    List<PostSlice.At> postsAbove =
        postSlice.items().subList(0, newPosts < 0 ? postSlice.items().size() : newPosts);
    if (notesAbove.size() + postsAbove.size() > SINCE_LIMIT) {
      return fresh(ask, hideFor);
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
    return new Page(merged.items(), null, top(noteSlice, postSlice, marker), false);
  }

  private Page fresh(HomeAsk ask, SeenViewer hideFor) {
    Page first = page(ask.fresh(hideFor));
    return new Page(first.items(), first.next(), first.newest(), true);
  }

  // The index of the marker in the list, which is how many came above it; -1 when it is not there.
  private static int above(List<Long> ids, long marker) {
    if (marker == 0) {
      return -1;
    }
    return ids.indexOf(marker);
  }

  private static String top(NoteSlice notes, PostSlice posts, SinceMarker before) {
    long note = notes.items().isEmpty() ? before.note() : notes.items().getFirst().note().id();
    long post =
        posts.items().isEmpty() ? before.post() : posts.items().getFirst().post().item().id();
    return new SinceMarker(note, post).toString();
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
}
