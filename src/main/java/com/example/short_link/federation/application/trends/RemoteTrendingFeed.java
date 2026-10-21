package com.example.short_link.federation.application.trends;

import com.example.short_link.common.note.NoteViewReader;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
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

  public record Page(
      List<Object> items, String next, @JsonInclude(JsonInclude.Include.NON_NULL) Boolean gap) {

    public Page(List<Object> items, String next) {
      this(items, next, null);
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

  public Page latest(Long viewerId, String cursor, String since, int limit) {
    int size = Math.clamp(limit, 1, TrendingPosts.MAX_LIMIT);
    if (latestProps.pulling()) {
      return stored(viewerId, cursor, since, size);
    }
    if (since != null && cursor == null) {
      LatestPosts.Page top = latest.page(viewerId, 0, TrendingPosts.MAX_LIMIT);
      List<String> uris = top.items().stream().map(TrendingNote::uri).toList();
      int at = uris.indexOf(since);
      if (at >= 0) {
        warm(viewerId, top.unknownAuthors());
        return new Page(List.copyOf(top.items().subList(0, at)), null, false);
      }
    }
    LatestPosts.Page page = latest.page(viewerId, offset(cursor), size);
    warm(viewerId, page.unknownAuthors());
    return new Page(
        List.copyOf(page.items()),
        page.more() ? "t" + (offset(cursor) + page.items().size()) : null,
        since != null && cursor == null ? Boolean.TRUE : null);
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
          return new Page(views(top.subList(0, at)), null, false);
        }
      }
    }
    Long beforeId = cursor != null && cursor.startsWith("r") ? id(cursor) : null;
    Page page = paged(notes.latest(viewerId, beforeId, size + 1), size);
    return fresh ? new Page(page.items(), page.next(), true) : page;
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
