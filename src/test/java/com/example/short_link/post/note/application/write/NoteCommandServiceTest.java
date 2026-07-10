package com.example.short_link.post.note.application.write;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.collection.CollectionConnectionCleaner;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.repository.NoteLikeRepository;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoteCommandServiceTest {

  @Mock private NoteRepository notes;
  @Mock private NoteLikeRepository likes;
  @Mock private CollectionConnectionCleaner connectionCleaner;

  private NoteCommandService service;

  @BeforeEach
  void setUp() {
    service = new NoteCommandService(notes, likes, connectionCleaner);
  }

  private NoteEntity note(long id, long ownerId) {
    NoteEntity n = new NoteEntity(ownerId, "some note");
    ReflectionTestUtils.setField(n, "id", id);
    return n;
  }

  @Test
  void deleteRemovesOwnNoteLikesAndConnections() {
    NoteEntity n = note(9L, 1L);
    when(notes.findById(9L)).thenReturn(Optional.of(n));

    service.delete(1L, 9L);

    verify(likes).deleteAllByNoteId(9L);
    // Curator connections pointing at this note go with it — otherwise the count overstates.
    verify(connectionCleaner).purgeForNote(9L);
    verify(notes).delete(n);
  }

  @Test
  void deleteRejectsForeignNote() {
    when(notes.findById(9L)).thenReturn(Optional.of(note(9L, 2L)));

    assertThatThrownBy(() -> service.delete(1L, 9L))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.NOTE_PERMISSION_DENIED);
    verify(notes, never()).delete(any());
    verify(connectionCleaner, never()).purgeForNote(anyLong());
  }

  @Test
  void deleteThrowsWhenMissing() {
    when(notes.findById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(1L, 9L))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.NOTE_NOT_FOUND);
    verify(connectionCleaner, never()).purgeForNote(anyLong());
  }
}
