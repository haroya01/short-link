package com.example.short_link.home.application.read;

import com.example.short_link.common.reply.ReplyPreview;
import com.example.short_link.home.application.read.HomeFeedQueryService.Item;
import com.example.short_link.home.application.read.HomeFeedQueryService.Merged;
import com.example.short_link.home.application.read.HomeFeedQueryService.Tab;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteSlice;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.post.application.read.PostCard;
import com.example.short_link.post.application.read.PostCommentQueryService;
import com.example.short_link.post.application.read.PostHomeSlices;
import com.example.short_link.post.application.read.PostSlice;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The cursor is "<notes>.<posts>", as on the home feed.
@Service
@RequiredArgsConstructor
public class RoomItemsQueryService {

  static final int MAX_LIMIT = 40;
  static final int REPLIES = 2;

  private final NoteQueryService notes;
  private final PostHomeSlices posts;
  private final PostCommentQueryService comments;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record RoomItem(String kind, NoteView note, PostCard post, List<Reply> replies) {}

  public record Reply(long id, ReplyPreview.Author author, String body) {}

  public record Page(List<RoomItem> items, String next) {}

  @Transactional(readOnly = true)
  public Page room(Long viewerId, Long roomId, String cursor, int limit) {
    int size = Math.clamp(limit, 1, MAX_LIMIT);
    HomeCursor.Positions at = HomeCursor.Positions.plain(cursor);
    NoteSlice noteSlice = notes.roomAt(viewerId, roomId, at.notes(), size + 1);
    PostSlice postSlice = posts.roomAt(viewerId, roomId, at.posts(), size + 1);
    return page(viewerId, at, size, noteSlice, postSlice);
  }

  @Transactional(readOnly = true)
  public Page rooms(Long viewerId, String cursor, int limit) {
    int size = Math.clamp(limit, 1, MAX_LIMIT);
    HomeCursor.Positions at = HomeCursor.Positions.plain(cursor);
    NoteSlice noteSlice = notes.roomsAt(viewerId, at.notes(), size + 1);
    PostSlice postSlice = posts.roomsAt(viewerId, at.posts(), size + 1);
    return page(viewerId, at, size, noteSlice, postSlice);
  }

  private Page page(
      Long viewerId, HomeCursor.Positions at, int size, NoteSlice noteSlice, PostSlice postSlice) {
    Merged merged =
        HomeFeedQueryService.merge(Tab.LATEST, noteSlice.items(), postSlice.items(), size);
    return new Page(
        withReplies(viewerId, merged.items()),
        HomeFeedQueryService.nextCursor(at, merged, noteSlice, postSlice, size));
  }

  private List<RoomItem> withReplies(Long viewerId, List<Item> merged) {
    List<Long> noteIds = new ArrayList<>();
    List<Long> postIds = new ArrayList<>();
    for (Item item : merged) {
      if (item.note() != null) {
        noteIds.add(item.note().id());
      } else if (item.post() != null) {
        postIds.add(item.post().item().id());
      }
    }
    Map<Long, List<ReplyPreview>> noteReplies =
        noteIds.isEmpty() ? Map.of() : notes.latestReplies(noteIds, viewerId, REPLIES);
    Map<Long, List<ReplyPreview>> postReplies =
        postIds.isEmpty() ? Map.of() : comments.latest(postIds, viewerId, REPLIES);
    List<RoomItem> items = new ArrayList<>(merged.size());
    for (Item item : merged) {
      NoteView note = item.note();
      PostCard post = item.post();
      if (note != null) {
        items.add(
            new RoomItem(
                "note", note, null, replies(noteReplies.getOrDefault(note.id(), List.of()))));
      } else if (post != null) {
        items.add(
            new RoomItem(
                "post",
                null,
                post,
                replies(postReplies.getOrDefault(post.item().id(), List.of()))));
      }
    }
    return items;
  }

  private static List<Reply> replies(List<ReplyPreview> previews) {
    return previews.stream()
        .map(reply -> new Reply(reply.id(), reply.author(), reply.body()))
        .toList();
  }
}
