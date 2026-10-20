package com.example.short_link.home.application.read;

import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteSlice;
import com.example.short_link.post.application.read.PostSlice;
import com.example.short_link.post.application.read.PublicFeedQueryService;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// One feed of notes and blog posts. Following and latest run newest first across both by a note's
// writing time and a post's publishing time (a repost stands at its note's time); trending takes
// the two trending orders in turn. The cursor is the next position in each source,
// "<notes>.<posts>".
@Service
@RequiredArgsConstructor
public class HomeFeedQueryService {

  static final int MAX_LIMIT = 40;
  static final int SINCE_LIMIT = 50;

  private final NoteQueryService notes;
  private final PublicFeedQueryService posts;

  public enum Tab {
    FOLLOWING,
    LATEST,
    TRENDING
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Item(String kind, Object note, Object post) {}

  public record Page(
      List<Item> items,
      String next,
      @JsonInclude(JsonInclude.Include.NON_NULL) String newest,
      @JsonInclude(JsonInclude.Include.NON_NULL) Boolean gap) {

    Page(List<Item> items, String next) {
      this(items, next, null, null);
    }
  }

  private record Merged(List<Item> items, int notesTaken, int postsTaken) {}

  public Page page(Long viewerId, String tab, String cursor, String since, int limit) {
    Tab chosen = tab(tab);
    if (chosen == Tab.FOLLOWING && viewerId == null) {
      return new Page(List.of(), null);
    }
    int size = Math.clamp(limit, 1, MAX_LIMIT);
    if (since != null && cursor == null) {
      return newer(viewerId, chosen, since, size);
    }
    int[] at = positions(cursor);
    NoteSlice noteSlice = notes(chosen, viewerId, at[0], size + 1);
    PostSlice postSlice = posts(chosen, viewerId, at[1], size + 1);
    Merged merged = merge(chosen, noteSlice.items(), postSlice.items(), size);
    int nextNote =
        next(
            at[0],
            noteSlice.items().stream().map(NoteSlice.At::position).toList(),
            merged.notesTaken(),
            size,
            noteSlice.exhausted());
    int nextPost =
        next(
            at[1],
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
        more ? nextNote + "." + nextPost : null,
        cursor == null ? top(noteSlice, postSlice, "0.0") : null,
        null);
  }

  // What came in above the newest note and post the reader already has, at most SINCE_LIMIT. A
  // marker no longer within reach (deleted, or more arrived than fit) is a gap: the reader gets a
  // fresh first page to replace the list with. Trending has no "above", so it always starts over.
  private Page newer(Long viewerId, Tab chosen, String since, int size) {
    long[] marker = markers(since);
    if (chosen == Tab.TRENDING || marker == null) {
      return fresh(viewerId, chosen, size);
    }
    NoteSlice noteSlice = notes(chosen, viewerId, 0, SINCE_LIMIT + 1);
    PostSlice postSlice = posts(chosen, viewerId, 0, SINCE_LIMIT + 1);
    int newNotes = above(noteSlice.items().stream().map(at -> at.note().id()).toList(), marker[0]);
    int newPosts =
        above(postSlice.items().stream().map(at -> at.post().item().id()).toList(), marker[1]);
    if ((newNotes < 0 && !(marker[0] == 0 && noteSlice.exhausted()))
        || (newPosts < 0 && !(marker[1] == 0 && postSlice.exhausted()))) {
      return fresh(viewerId, chosen, size);
    }
    List<NoteSlice.At> notesAbove =
        noteSlice.items().subList(0, newNotes < 0 ? noteSlice.items().size() : newNotes);
    List<PostSlice.At> postsAbove =
        postSlice.items().subList(0, newPosts < 0 ? postSlice.items().size() : newPosts);
    if (notesAbove.size() + postsAbove.size() > SINCE_LIMIT) {
      return fresh(viewerId, chosen, size);
    }
    Merged merged = merge(chosen, notesAbove, postsAbove, SINCE_LIMIT);
    return new Page(merged.items(), null, top(noteSlice, postSlice, since), false);
  }

  private Page fresh(Long viewerId, Tab chosen, int size) {
    Page first = page(viewerId, chosen.name(), null, null, size);
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

  private NoteSlice notes(Tab chosen, Long viewerId, int offset, int limit) {
    return switch (chosen) {
      case FOLLOWING -> notes.followingAt(viewerId, offset, limit);
      case LATEST -> notes.everyoneAt(viewerId, offset, limit);
      case TRENDING -> notes.trendingAt(viewerId, offset, limit);
    };
  }

  private PostSlice posts(Tab chosen, Long viewerId, int offset, int limit) {
    return switch (chosen) {
      case FOLLOWING -> posts.followingAt(viewerId, offset, limit);
      case LATEST -> posts.recentAt(viewerId, offset, limit);
      case TRENDING -> posts.trendingAt(viewerId, offset, limit);
    };
  }

  private static Merged merge(
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
        items.add(new Item("note", noteQueue.get(n++).note(), null));
      } else {
        items.add(new Item("post", null, postQueue.get(p++).post()));
      }
      noteTurn = !takeNote;
    }
    return new Merged(items, n, p);
  }

  private static int next(
      int from, List<Integer> positions, int taken, int size, boolean exhausted) {
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
      return Tab.valueOf(raw.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      return Tab.LATEST;
    }
  }

  private static int[] positions(String cursor) {
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
