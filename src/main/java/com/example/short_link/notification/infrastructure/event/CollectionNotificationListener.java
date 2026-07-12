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
 * Turns a {@link CollectionConnectedEvent} into the two graph-bell notices, closing the reading
 * graph's retention loop: CONNECTED tells the woven work's author "someone put your work in their
 * path," and PATH_GREW tells everyone already in that path "a path you're part of grew." Like its
 * sibling {@link PostNotificationListener} it fires only after the connect commits and runs on the
 * shared {@code webhookExecutor}, so the recording insert never stalls the request path.
 *
 * <p>Both notices point at the collection, so they share one {@link NotificationCollectionRef}
 * payload. The producer already resolved and deduped every recipient, so this stays a thin fan-out
 * with no post→data lookup: the author self-connecting is dropped up front (a null recipient), and
 * PATH_GREW's recipient list already excludes the woven author and the connecting curator.
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

    // CONNECTED → the woven work's author. Null when the curator wove their own work (or a note,
    // whose author is the curator), so no self-notice fires.
    if (event.connectedAuthorUserId() != null
        && !event.connectedAuthorUserId().equals(event.actorUserId())) {
      recordUseCase.record(
          event.connectedAuthorUserId(),
          NotificationType.CONNECTED,
          event.actorUserId(),
          collection);
    }

    // PATH_GREW → every distinct prior contributor. The list is already deduped and already
    // excludes the woven author and the curator, so an empty list is a silent no-op.
    recordUseCase.recordForEach(
        event.priorContributorUserIds(),
        NotificationType.PATH_GREW,
        event.actorUserId(),
        collection);
  }
}
