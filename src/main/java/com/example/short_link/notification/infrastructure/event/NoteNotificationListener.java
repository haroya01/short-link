package com.example.short_link.notification.infrastructure.event;

import com.example.short_link.common.event.Actor;
import com.example.short_link.common.event.NoteBroadcastEvent;
import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.NotePollEndedEvent;
import com.example.short_link.common.event.NoteRevisedEvent;
import com.example.short_link.common.event.PostQuotedEvent;
import com.example.short_link.notification.application.dto.NotificationNoteRef;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Likes and reposts of one note group by UTC day, so a burst from other servers reads as one row
// that grows instead of a screen of copies. Posts group the same way.
@Component
@RequiredArgsConstructor
public class NoteNotificationListener {

  private final RecordBlogNotificationUseCase recordUseCase;
  private final NotificationFollowerReader followers;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNoteInteraction(NoteInteractionEvent event) {
    if (event.isSelfAction() || event.recipientUserId() == null) {
      return;
    }
    NotificationType type =
        switch (event.type()) {
          case LIKE -> NotificationType.NOTE_LIKE;
          case REPOST -> NotificationType.NOTE_REPOST;
          case REPLY -> NotificationType.NOTE_REPLY;
          case QUOTE -> NotificationType.NOTE_QUOTE;
          case MENTION -> NotificationType.NOTE_MENTION;
        };
    recordUseCase.record(
        event.recipientUserId(),
        type,
        event.actor(),
        new NotificationNoteRef(
            event.noteId(), event.noteExcerpt(), event.sourceNoteId(), event.sourceExcerpt()),
        NotificationGroupKey.of(type, event.noteId()),
        event.conversationId());
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPostQuoted(PostQuotedEvent event) {
    if (event.isSelfAction() || event.recipientUserId() == null) {
      return;
    }
    recordUseCase.record(
        event.recipientUserId(),
        NotificationType.POST_QUOTE,
        Actor.member(event.actorUserId()),
        new NotificationNoteRef(event.noteId(), event.noteExcerpt(), null, null));
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNoteBroadcast(NoteBroadcastEvent event) {
    List<Long> subscribers = followers.noteSubscribersOf(event.authorId());
    if (subscribers.isEmpty()) {
      return;
    }
    recordUseCase.recordForEach(
        subscribers,
        NotificationType.NOTE_POST,
        Actor.member(event.authorId()),
        new NotificationNoteRef(event.noteId(), event.excerpt(), null, null));
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNoteRevised(NoteRevisedEvent event) {
    List<Long> sharers =
        followers.noteSharersOf(event.noteId(), event.author().userId(), event.author().remoteId());
    if (sharers.isEmpty()) {
      return;
    }
    recordUseCase.recordForEach(
        sharers,
        NotificationType.NOTE_EDIT,
        event.author(),
        new NotificationNoteRef(event.noteId(), event.excerpt(), null, null));
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPollEnded(NotePollEndedEvent event) {
    NotificationNoteRef note =
        new NotificationNoteRef(event.noteId(), event.noteExcerpt(), null, null);
    recordUseCase.record(
        event.authorId(), NotificationType.NOTE_POLL, Actor.member(event.authorId()), note);
    List<Long> voters =
        event.voterIds().stream().filter(voter -> !voter.equals(event.authorId())).toList();
    recordUseCase.recordForEachWhoHears(
        voters, NotificationType.NOTE_POLL, Actor.member(event.authorId()), note);
  }
}
