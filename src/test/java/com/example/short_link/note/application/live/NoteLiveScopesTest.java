package com.example.short_link.note.application.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.live.LiveScope;
import com.example.short_link.common.live.LiveTopic;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NotePageReader;
import com.example.short_link.note.domain.repository.NoteRoomReader;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NoteLiveScopesTest {

  private final NoteQueryService query = mock(NoteQueryService.class);
  private final NotePageReader pages = mock(NotePageReader.class);
  private final NoteRoomReader rooms = mock(NoteRoomReader.class);
  private final NoteRepliesLiveScope replies = new NoteRepliesLiveScope(query, pages);
  private final RoomNotesLiveScope roomNotes = new RoomNotesLiveScope(rooms);

  @Test
  void aThreadOpensForWhoeverItsReadApiShowsItToAndCarriesItsWriterAndRoom() {
    NoteEntity note = new NoteEntity(7L, "hello", null, null);
    note.postIn(9L);
    when(query.readable(2L, 30L)).thenReturn(Optional.of(note));
    when(query.readable(3L, 30L)).thenReturn(Optional.empty());

    assertThat(replies.scope()).isEqualTo(LiveTopic.Scope.NOTE);
    assertThat(replies.open(30L, 2L, null)).contains(new LiveScope(7L, 9L));
    assertThat(replies.open(30L, 3L, null)).isEmpty();
  }

  @Test
  void aThreadsLatestIsItsNewestDirectReply() {
    when(pages.latestReply(30L)).thenReturn(41L);

    assertThat(replies.latest(30L)).isEqualTo(41L);
  }

  @Test
  void aRoomOpensForItsMembersAlone() {
    when(rooms.isMember(9L, 2L)).thenReturn(true);
    when(rooms.isMember(9L, 3L)).thenReturn(false);
    when(rooms.latestNote(9L)).thenReturn(55L);

    assertThat(roomNotes.scope()).isEqualTo(LiveTopic.Scope.ROOM);
    assertThat(roomNotes.open(9L, 2L, null)).contains(new LiveScope(null, 9L));
    assertThat(roomNotes.open(9L, 3L, null)).isEmpty();
    assertThat(roomNotes.latest(9L)).isEqualTo(55L);
  }
}
