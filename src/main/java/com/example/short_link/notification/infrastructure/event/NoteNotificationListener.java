package com.example.short_link.notification.infrastructure.event;

import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.RemoteFollowedEvent;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.notification.application.dto.NotificationNoteRef;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNoteInteraction(NoteInteractionEvent event) {
    if (event.isSelfAction() || event.recipientUserId() == null) {
      return;
    }
    if (event.actorUserId() != null
        && blocks.isBlocked(event.recipientUserId(), event.actorUserId())) {
      return;
    }
    NotificationType type =
        switch (event.type()) {
          case LIKE -> NotificationType.NOTE_LIKE;
          case REPOST -> NotificationType.NOTE_REPOST;
          case REPLY -> NotificationType.NOTE_REPLY;
          case QUOTE -> NotificationType.NOTE_QUOTE;
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
  public void onRemoteFollowed(RemoteFollowedEvent event) {
    recordUseCase.record(
        event.userId(), NotificationType.REMOTE_FOLLOW, null, event.remoteActorId(), null, null);
  }

  static String groupKey(NotificationType type, Long noteId) {
    return type.name() + ":" + noteId + ":" + LocalDate.now(ZoneOffset.UTC);
  }
}
