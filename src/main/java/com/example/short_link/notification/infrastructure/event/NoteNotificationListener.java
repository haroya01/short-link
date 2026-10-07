package com.example.short_link.notification.infrastructure.event;

import com.example.short_link.common.event.NoteBroadcastEvent;
import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.NotePollEndedEvent;
import com.example.short_link.common.event.NoteRevisedEvent;
import com.example.short_link.common.event.RemoteFollowedEvent;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.notification.application.dto.NotificationNoteRef;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Likes and reposts of one note group by UTC day, so a burst from other servers reads as one row
// that grows instead of a screen of copies.
@Component
@RequiredArgsConstructor
public class NoteNotificationListener {

  private final RecordBlogNotificationUseCase recordUseCase;
  private final UserBlockChecker blocks;
  private final NotificationFollowerReader followers;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNoteInteraction(NoteInteractionEvent event) {
    if (event.isSelfAction() || event.recipientUserId() == null) {
      return;
    }
    if (blocks.silences(
        event.recipientUserId(),
        event.actorUserId(),
        event.actorRemoteId(),
        event.conversationId())) {
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
        event.actorUserId(),
        event.actorRemoteId(),
        new NotificationNoteRef(
            event.noteId(), event.noteExcerpt(), event.sourceNoteId(), event.sourceExcerpt()),
        type.grouped() ? groupKey(type, event.noteId()) : null);
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
        event.authorId(),
        new NotificationNoteRef(event.noteId(), event.excerpt(), null, null));
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNoteRevised(NoteRevisedEvent event) {
    List<Long> sharers =
        followers.noteSharersOf(event.noteId(), event.authorUserId(), event.authorRemoteId());
    if (sharers.isEmpty()) {
      return;
    }
    recordUseCase.recordForEach(
        sharers,
        NotificationType.NOTE_EDIT,
        event.authorUserId(),
        event.authorRemoteId(),
        new NotificationNoteRef(event.noteId(), event.excerpt(), null, null));
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRemoteFollowed(RemoteFollowedEvent event) {
    recordUseCase.record(
        event.userId(), NotificationType.REMOTE_FOLLOW, null, event.remoteActorId(), null, null);
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPollEnded(NotePollEndedEvent event) {
    NotificationNoteRef note =
        new NotificationNoteRef(event.noteId(), event.noteExcerpt(), null, null);
    recordUseCase.record(
        event.authorId(), NotificationType.NOTE_POLL, event.authorId(), null, note, null);
    List<Long> voters =
        event.voterIds().stream()
            .filter(voter -> !voter.equals(event.authorId()))
            .filter(voter -> !blocks.silences(voter, event.authorId()))
            .toList();
    recordUseCase.recordForEach(voters, NotificationType.NOTE_POLL, event.authorId(), note);
  }

  static String groupKey(NotificationType type, Long noteId) {
    return type.name() + ":" + noteId + ":" + LocalDate.now(ZoneOffset.UTC);
  }
}
