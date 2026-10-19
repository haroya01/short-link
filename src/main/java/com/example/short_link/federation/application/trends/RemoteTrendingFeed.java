package com.example.short_link.federation.application.trends;

import com.example.short_link.common.note.NoteViewReader;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Pages through what other servers trend on, then, for a member, on through the notes from
// elsewhere this server keeps, newest first. A cursor is t<offset> in the first part and r<id>
// (the last note shown) in the second.
@Service
@RequiredArgsConstructor
public class RemoteTrendingFeed {

  private final TrendingPosts trending;
  private final TrendingAuthors authors;
  private final NoteViewReader notes;

  public record Page(List<Object> items, String next) {}

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

  private Page kept(Long viewerId, Long beforeId, int size) {
    List<NoteViewReader.Listed> rows = notes.received(viewerId, beforeId, size + 1);
    boolean more = rows.size() > size;
    List<NoteViewReader.Listed> shown = more ? rows.subList(0, size) : rows;
    List<Object> items = new ArrayList<>(shown.size());
    shown.forEach(row -> items.add(row.view()));
    return new Page(items, more ? "r" + shown.getLast().id() : null);
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
