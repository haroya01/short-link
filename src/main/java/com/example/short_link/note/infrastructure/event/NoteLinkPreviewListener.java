package com.example.short_link.note.infrastructure.event;

import com.example.short_link.note.application.write.NoteLinkPreviewRequested;
import com.example.short_link.note.application.write.NoteLinkPreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// After commit and off the request thread: the page fetch can take seconds and must never hold the
// note's transaction or delay the post.
@Component
@RequiredArgsConstructor
public class NoteLinkPreviewListener {

  private final NoteLinkPreviewService previews;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRequested(NoteLinkPreviewRequested event) {
    previews.refresh(event.noteId(), event.url());
  }
}
