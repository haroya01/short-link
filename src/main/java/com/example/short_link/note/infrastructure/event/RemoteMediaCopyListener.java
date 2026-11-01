package com.example.short_link.note.infrastructure.event;

import com.example.short_link.note.application.media.RemoteImagesSaved;
import com.example.short_link.note.application.media.RemoteMediaQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RemoteMediaCopyListener {

  private final RemoteMediaQueue queue;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onSaved(RemoteImagesSaved event) {
    event.images().forEach(queue::enqueue);
  }
}
