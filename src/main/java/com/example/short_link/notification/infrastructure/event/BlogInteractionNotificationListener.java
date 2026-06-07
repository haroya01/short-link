package com.example.short_link.notification.infrastructure.event;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.dto.NotificationSeriesRef;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes an in-app notification when a reader likes/comments/follows/subscribes. The sibling of
 * {@link com.example.short_link.post.webhook.scheduler.BlogWebhookDispatcher}: it consumes the same
 * {@link BlogInteractionEvent}, fires only after the interaction commits (so a rolled-back like
 * leaves no phantom notification), and runs on the shared {@code webhookExecutor} so the recording
 * insert never stalls the request path.
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
    recordUseCase.record(event.recipientUserId(), type, event.actorUserId(), payloadOf(event));
  }

  /**
   * The target ref serialized into the row's payload: a series ref for SERIES_SUBSCRIBE, a post ref
   * (no author handle — the recipient IS the author for LIKE/COMMENT) when a post is present, else
   * null (FOLLOW).
   */
  private static Object payloadOf(BlogInteractionEvent event) {
    if (event.type() == BlogInteractionType.SERIES_SUBSCRIBE) {
      return new NotificationSeriesRef(event.seriesId(), event.seriesSlug(), event.seriesTitle());
    }
    if (event.postId() == null) {
      return null;
    }
    return new NotificationPostRef(event.postId(), event.postSlug(), event.postTitle(), null);
  }

  private static NotificationType mapType(BlogInteractionType type) {
    return switch (type) {
      case LIKE -> NotificationType.LIKE;
      case COMMENT -> NotificationType.COMMENT;
      case FOLLOW -> NotificationType.FOLLOW;
      case SERIES_SUBSCRIBE -> NotificationType.SERIES_SUBSCRIBE;
    };
  }
}
