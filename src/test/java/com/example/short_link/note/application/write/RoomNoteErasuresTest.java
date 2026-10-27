package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.RoomErasure;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.RoomNoteErasureRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomNoteErasuresTest {

  private static final Instant NOW = Instant.parse("2026-10-27T03:00:00.123456789Z");

  @Mock private RoomNoteErasureRepository erasures;
  @Mock private NoteRepository notes;
  @Mock private NoteThreadControls threads;

  private RoomNoteErasures service() {
    return new RoomNoteErasures(
        erasures, notes, threads, FakeTransactions.template(), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static NoteEntity note(Long id) {
    NoteEntity note = new NoteEntity(7L, "n" + id, null, null);
    ReflectionTestUtils.setField(note, "id", id);
    return note;
  }

  @Test
  void aRequestIsKeptAtTheMicrosecond() {
    service().request(7L, 30L);

    verify(erasures).requestRoomErasure(7L, 30L, Instant.parse("2026-10-27T03:00:00.123456Z"));
  }

  @Test
  void eachRequestLosesOneChunkInItsOwnTransactionAndAShortChunkFinishesIt() {
    List<String> seen = new ArrayList<>();
    List<Long> full = new ArrayList<>();
    for (long id = 1; id <= RoomNoteErasures.CHUNK; id++) {
      full.add(id);
    }
    when(erasures.pendingRoomErasures(RoomNoteErasures.REQUESTS_PER_TICK))
        .thenReturn(List.of(new RoomErasure(7L, 30L), new RoomErasure(8L, 31L)));
    when(erasures.roomNoteIdsBy(7L, 30L, RoomNoteErasures.CHUNK)).thenReturn(full);
    when(erasures.roomNoteIdsBy(8L, 31L, RoomNoteErasures.CHUNK)).thenReturn(List.of(99L));
    when(notes.findAllByIdIn(any())).thenReturn(List.of(note(1L)));
    doAnswer(
            inv -> {
              seen.add("remove " + FakeTransactions.where());
              return null;
            })
        .when(threads)
        .remove(any());

    assertThat(service().eraseDue()).isEqualTo(RoomNoteErasures.CHUNK + 1);

    assertThat(seen).containsExactly("remove inside", "remove inside");
    verify(erasures, never()).finishRoomErasure(7L, 30L);
    verify(erasures).finishRoomErasure(8L, 31L);
  }
}
