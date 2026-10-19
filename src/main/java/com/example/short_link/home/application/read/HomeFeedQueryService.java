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

  private final NoteQueryService notes;
  private final PublicFeedQueryService posts;

  public enum Tab {
    FOLLOWING,
    LATEST,
    TRENDING
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Item(String kind, Object note, Object post) {}

  public record Page(List<Item> items, String next) {}

  public Page page(Long viewerId, String tab, String cursor, int limit) {
    Tab chosen = tab(tab);
    if (chosen == Tab.FOLLOWING && viewerId == null) {
      return new Page(List.of(), null);
    }
    int size = Math.clamp(limit, 1, MAX_LIMIT);
    int[] at = positions(cursor);
    NoteSlice noteSlice =
        switch (chosen) {
          case FOLLOWING -> notes.followingAt(viewerId, at[0], size + 1);
          case LATEST -> notes.everyoneAt(viewerId, at[0], size + 1);
          case TRENDING -> notes.trendingAt(viewerId, at[0], size + 1);
        };
    PostSlice postSlice =
        switch (chosen) {
          case FOLLOWING -> posts.followingAt(viewerId, at[1], size + 1);
          case LATEST -> posts.recentAt(viewerId, at[1], size + 1);
          case TRENDING -> posts.trendingAt(viewerId, at[1], size + 1);
        };
    List<NoteSlice.At> noteQueue = noteSlice.items();
    List<PostSlice.At> postQueue = postSlice.items();
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
            !noteQueue.get(n).note().createdAt().isBefore(postQueue.get(p).post().publishedAt());
      }
      if (takeNote) {
        items.add(new Item("note", noteQueue.get(n++).note(), null));
      } else {
        items.add(new Item("post", null, postQueue.get(p++).post()));
      }
      noteTurn = !takeNote;
    }
    int nextNote =
        next(
            at[0],
            noteQueue.stream().map(NoteSlice.At::position).toList(),
            n,
            size,
            noteSlice.exhausted());
    int nextPost =
        next(
            at[1],
            postQueue.stream().map(PostSlice.At::position).toList(),
            p,
            size,
            postSlice.exhausted());
    boolean more =
        n < noteQueue.size()
            || p < postQueue.size()
            || !noteSlice.exhausted()
            || !postSlice.exhausted();
    return new Page(items, more ? nextNote + "." + nextPost : null);
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
