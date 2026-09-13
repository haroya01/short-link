package com.example.short_link.notification.infrastructure.event;

import com.example.short_link.common.event.CollectionConnectedEvent;
import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Recipients are resolved and deduplicated by the event producer; both notices target the
 * collection. AFTER_COMMIT prevents notifications for rolled-back connections.
 */
@Component
@RequiredArgsConstructor
public class CollectionNotificationListener {

  private final RecordBlogNotificationUseCase recordUseCase;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCollectionConnected(CollectionConnectedEvent event) {
    NotificationCollectionRef collection =
        new NotificationCollectionRef(
            event.collectionId(), event.collectionName(), event.connectedPostId());

    // A null author denotes a self-connection or curator-owned note.
    if (event.connectedAuthorUserId() != null
        && !event.connectedAuthorUserId().equals(event.actorUserId())) {
      recordUseCase.record(
          event.connectedAuthorUserId(),
          NotificationType.CONNECTED,
          event.actorUserId(),
          collection);
    }

    // The producer deduplicates prior contributors and excludes the connected author and curator.
    recordUseCase.recordForEach(
        event.priorContributorUserIds(),
        NotificationType.PATH_GREW,
        event.actorUserId(),
        collection);
  }
}
