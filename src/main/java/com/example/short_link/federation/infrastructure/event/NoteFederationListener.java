package com.example.short_link.federation.infrastructure.event;

import com.example.short_link.common.event.AccountDeletedEvent;
import com.example.short_link.common.event.NoteDeletedEvent;
import com.example.short_link.common.event.NoteEditedEvent;
import com.example.short_link.common.event.NotePublishedEvent;
import com.example.short_link.common.event.NoteRepostedEvent;
import com.example.short_link.common.event.NoteUnrepostedEvent;
import com.example.short_link.federation.application.FederationLeaving;
import com.example.short_link.federation.application.NoteFederation;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// After commit only, so a rolled-back note or deletion never reaches another server; queueing runs
// off the request thread.
@Component
@RequiredArgsConstructor
public class NoteFederationListener {

  private final NoteFederation notes;
  private final FederationLeaving leaving;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPublished(NotePublishedEvent event) {
    notes.created(event.noteId(), event.authorId());
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEdited(NoteEditedEvent event) {
    notes.edited(event.noteId(), event.authorId());
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDeleted(NoteDeletedEvent event) {
    notes.deleted(event.noteId(), event.authorId());
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReposted(NoteRepostedEvent event) {
    notes.reposted(event.repostId(), event.noteId(), event.userId());
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUnreposted(NoteUnrepostedEvent event) {
    notes.unreposted(event.repostId(), event.noteId(), event.userId());
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onAccountDeleted(AccountDeletedEvent event) {
    leaving.leave(event.userId());
  }
}
