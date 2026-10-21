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
import com.example.short_link.post.application.read.PostSlice;
import com.example.short_link.post.application.read.PublicFeedQueryService;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// A room's notes and blog posts as one list, newest first by a note's writing time and a post's
// publishing time, each with its latest replies; or the same across every room of the member. The
// cursor is the next position in each source, "<notes>.<posts>", as on the home feed.
@Service
@RequiredArgsConstructor
public class RoomItemsQueryService {

  static final int MAX_LIMIT = 40;
  static final int REPLIES = 2;

  private final NoteQueryService notes;
  private final PublicFeedQueryService posts;
  private final PostCommentQueryService comments;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record RoomItem(String kind, Object note, Object post, List<Reply> replies) {}

  public record Reply(long id, ReplyPreview.Author author, String body) {}

  public record Page(List<RoomItem> items, String next) {}

  @Transactional(readOnly = true)
  public Page room(Long roomId, Long viewerId, String cursor, int limit) {
    int size = Math.clamp(limit, 1, MAX_LIMIT);
    int[] at = HomeFeedQueryService.positions(cursor);
    NoteSlice noteSlice = notes.roomAt(roomId, viewerId, at[0], size + 1);
    PostSlice postSlice = posts.roomAt(roomId, viewerId, at[1], size + 1);
    return page(viewerId, at, size, noteSlice, postSlice);
  }

  @Transactional(readOnly = true)
  public Page rooms(Long viewerId, String cursor, int limit) {
    int size = Math.clamp(limit, 1, MAX_LIMIT);
    int[] at = HomeFeedQueryService.positions(cursor);
    NoteSlice noteSlice = notes.roomsAt(viewerId, at[0], size + 1);
    PostSlice postSlice = posts.roomsAt(viewerId, at[1], size + 1);
    return page(viewerId, at, size, noteSlice, postSlice);
  }

  private Page page(Long viewerId, int[] at, int size, NoteSlice noteSlice, PostSlice postSlice) {
    Merged merged =
        HomeFeedQueryService.merge(Tab.LATEST, noteSlice.items(), postSlice.items(), size);
    List<Long> noteIds = new ArrayList<>();
    List<Long> postIds = new ArrayList<>();
    for (Item item : merged.items()) {
      if (item.note() instanceof NoteView note) {
        noteIds.add(note.id());
      } else if (item.post() instanceof PostCard post) {
        postIds.add(post.item().id());
      }
    }
    Map<Long, List<ReplyPreview>> noteReplies =
        noteIds.isEmpty() ? Map.of() : notes.latestReplies(noteIds, viewerId, REPLIES);
    Map<Long, List<ReplyPreview>> postReplies =
        postIds.isEmpty() ? Map.of() : comments.latest(postIds, viewerId, REPLIES);
    List<RoomItem> items = new ArrayList<>(merged.items().size());
    for (Item item : merged.items()) {
      if (item.note() instanceof NoteView note) {
        items.add(
            new RoomItem(
                "note", note, null, replies(noteReplies.getOrDefault(note.id(), List.of()))));
      } else if (item.post() instanceof PostCard post) {
        items.add(
            new RoomItem(
                "post",
                null,
                post,
                replies(postReplies.getOrDefault(post.item().id(), List.of()))));
      }
    }
    int nextNote =
        HomeFeedQueryService.next(
            at[0],
            noteSlice.items().stream().map(NoteSlice.At::position).toList(),
            merged.notesTaken(),
            size,
            noteSlice.exhausted());
    int nextPost =
        HomeFeedQueryService.next(
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
    return new Page(items, more ? nextNote + "." + nextPost : null);
  }

  private static List<Reply> replies(List<ReplyPreview> previews) {
    return previews.stream()
        .map(reply -> new Reply(reply.id(), reply.author(), reply.body()))
        .toList();
  }
}
