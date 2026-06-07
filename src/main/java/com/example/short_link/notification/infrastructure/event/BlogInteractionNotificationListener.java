package com.example.short_link.notification.infrastructure.event;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes an in-app notification when a reader likes/comments/follows. The sibling of {@link
 * com.example.short_link.post.webhook.scheduler.BlogWebhookDispatcher}: it consumes the same {@link
 * BlogInteractionEvent}, fires only after the interaction commits (so a rolled-back like leaves no
 * phantom notification), and runs on the shared {@code webhookExecutor} so the recording insert
 * never stalls the request path.
 */
@Component
@RequiredArgsConstructor
public class BlogInteractionNotificationListener {

  private final RecordBlogNotificationUseCase recordUseCase;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onBlogInteraction(BlogInteractionEvent event) {
    if (event.isSelfAction() || event.recipientUserId() == null) {
      return;
    }
    NotificationType type = mapType(event.type());
    if (type == null) {
      return;
    }
    NotificationPostRef post =
        event.postId() == null
            ? null
            : new NotificationPostRef(event.postId(), event.postSlug(), event.postTitle());
    recordUseCase.record(event.recipientUserId(), type, event.actorUserId(), post);
  }

  /** Maps the interaction kind onto the bell's surface; SERIES_SUBSCRIBE isn't surfaced yet. */
  private static NotificationType mapType(BlogInteractionType type) {
    return switch (type) {
      case LIKE -> NotificationType.LIKE;
      case COMMENT -> NotificationType.COMMENT;
      case FOLLOW -> NotificationType.FOLLOW;
      case SERIES_SUBSCRIBE -> null;
    };
  }
}
