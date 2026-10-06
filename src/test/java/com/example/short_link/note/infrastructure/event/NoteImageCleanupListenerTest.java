package com.example.short_link.note.infrastructure.event;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.NoteDeletedEvent;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoteImageCleanupListenerTest {

  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final NoteImageCleanupListener listener = new NoteImageCleanupListener(storage);

  @Test
  void deletesEveryImageAndCarriesOnPastFailures() {
    when(storage.isConfigured()).thenReturn(true);
    doThrow(new ObjectStorageException("down", null)).when(storage).delete("a");

    listener.onNoteDeleted(new NoteDeletedEvent(1L, 7L, List.of("a", "b")));

    verify(storage).delete("a");
    verify(storage).delete("b");
  }

  @Test
  void nothingToDoWithoutImagesOrStorage() {
    listener.onNoteDeleted(new NoteDeletedEvent(1L, 7L, List.of()));
    when(storage.isConfigured()).thenReturn(false);
    listener.onNoteDeleted(new NoteDeletedEvent(1L, 7L, List.of("a")));

    verify(storage, never()).delete("a");
  }
}
