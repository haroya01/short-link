package com.example.short_link.federation.infrastructure.event;

import com.example.short_link.common.event.AccountDeletedEvent;
import com.example.short_link.common.event.NoteDeletedEvent;
import com.example.short_link.common.event.NoteEditedEvent;
import com.example.short_link.common.event.NotePollEndedEvent;
import com.example.short_link.common.event.NotePublishedEvent;
import com.example.short_link.common.event.NoteRepostedEvent;
import com.example.short_link.common.event.NoteUnrepostedEvent;
import com.example.short_link.common.event.RemoteNoteLikedEvent;
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
    if (event.reachesElsewhere()) {
      notes.createdElsewhere(event.noteId(), event.authorId());
    } else {
      notes.created(event.noteId(), event.authorId());
    }
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEdited(NoteEditedEvent event) {
    notes.edited(event.noteId(), event.authorId(), event.reachesElsewhere());
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPollEnded(NotePollEndedEvent event) {
    notes.pollEnded(event.noteId(), event.authorId());
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDeleted(NoteDeletedEvent event) {
    notes.deleted(event.noteId(), event.authorId(), event.inReplyToId(), event.remoteHandles());
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReposted(NoteRepostedEvent event) {
    if (event.remote()) {
      notes.repostedRemote(event.repostId(), event.noteId(), event.userId(), true);
    } else {
      notes.reposted(event.repostId(), event.noteId(), event.userId());
    }
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUnreposted(NoteUnrepostedEvent event) {
    if (event.remote()) {
      notes.repostedRemote(event.repostId(), event.noteId(), event.userId(), false);
    } else {
      notes.unreposted(event.repostId(), event.noteId(), event.userId());
    }
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRemoteNoteLiked(RemoteNoteLikedEvent event) {
    notes.likedRemote(event.noteId(), event.userId(), event.liked());
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onAccountDeleted(AccountDeletedEvent event) {
    leaving.leave(event.userId());
  }
}
