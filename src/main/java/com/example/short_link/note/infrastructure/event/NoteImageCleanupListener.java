package com.example.short_link.note.infrastructure.event;

import com.example.short_link.common.event.NoteDeletedEvent;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// After commit only: a rolled-back delete must keep its images.
@Slf4j
@Component
@RequiredArgsConstructor
public class NoteImageCleanupListener {

  private final ObjectStorage storage;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNoteDeleted(NoteDeletedEvent event) {
    if (event.mediaKeys().isEmpty() || !storage.isConfigured()) {
      return;
    }
    for (String key : event.mediaKeys()) {
      try {
        storage.delete(key);
      } catch (ObjectStorageException e) {
        log.warn("failed to delete note image key={}", key, e);
      }
    }
  }
}
