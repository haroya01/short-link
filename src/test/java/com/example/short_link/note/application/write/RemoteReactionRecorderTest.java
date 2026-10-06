package com.example.short_link.note.application.write;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.note.RemoteNoteReactions.Kind;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteRemoteReactionRepository;
import com.example.short_link.note.domain.repository.NoteRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RemoteReactionRecorderTest {

  @Mock private NoteRemoteReactionRepository reactions;
  @Mock private NoteRepository notes;
  @Mock private ApplicationEventPublisher events;

  private RemoteReactionRecorder recorder() {
    return new RemoteReactionRecorder(reactions, notes, events);
  }

  @Test
  void onlyANewReactionTellsTheAuthor() {
    NoteEntity note = new NoteEntity(9L, "hello  there", null, null);
    ReflectionTestUtils.setField(note, "id", 5L);
    when(reactions.add(5L, 7L, Kind.ANNOUNCE, "a1")).thenReturn(true);
    when(notes.findById(5L)).thenReturn(Optional.of(note));

    recorder().add(5L, 7L, Kind.ANNOUNCE, "a1");

    verify(events)
        .publishEvent(
            new NoteInteractionEvent(
                NoteInteractionEvent.Type.REPOST, 9L, null, 7L, 5L, "hello there", null, null));
  }

  @Test
  void aResentReactionIsQuiet() {
    when(reactions.add(5L, 7L, Kind.LIKE, "l2")).thenReturn(false);

    recorder().add(5L, 7L, Kind.LIKE, "l2");

    verifyNoInteractions(notes, events);
  }
}
