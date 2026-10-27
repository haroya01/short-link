package com.example.short_link.home.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteSlice;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.read.NoteViewFixtures;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.post.application.read.PostCommentQueryService;
import com.example.short_link.post.application.read.PostHomeSlices;
import com.example.short_link.post.application.read.PostSlice;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomItemsQueryServiceTest {

  @Mock private NoteQueryService notes;
  @Mock private PostHomeSlices posts;
  @Mock private PostCommentQueryService comments;

  @Test
  void aRoomShowsItsNotesAndPostsToTheMemberAskingForThatRoom() {
    NoteView note = NoteViewFixtures.note(5L, "방에서", new NoteAuthor(7L, "me", null), null, 0L);
    when(notes.roomAt(7L, 30L, 0, 21))
        .thenReturn(new NoteSlice(List.of(new NoteSlice.At(0, note)), true));
    when(posts.roomAt(7L, 30L, 0, 21)).thenReturn(new PostSlice(List.of(), true));
    when(notes.latestReplies(anyList(), eq(7L), eq(RoomItemsQueryService.REPLIES)))
        .thenReturn(Map.of());

    RoomItemsQueryService.Page page =
        new RoomItemsQueryService(notes, posts, comments).room(7L, 30L, null, 20);

    assertThat(page.items()).extracting(RoomItemsQueryService.RoomItem::note).containsExactly(note);
    assertThat(page.next()).isNull();
  }
}
