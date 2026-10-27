package com.example.short_link.notification.infrastructure.event;

import com.example.short_link.common.event.AccountUnlockedEvent;
import com.example.short_link.common.event.Actor;
import com.example.short_link.common.event.FollowRequestSettledEvent;
import com.example.short_link.common.event.FollowRequestedEvent;
import com.example.short_link.common.event.RemoteFollowedEvent;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FollowNotificationListener {

  private final NotificationRepository notifications;
  private final RecordBlogNotificationUseCase recordUseCase;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRemoteFollowed(RemoteFollowedEvent event) {
    recordUseCase.record(
        event.userId(), NotificationType.REMOTE_FOLLOW, Actor.remote(event.remoteActorId()), null);
  }

  // Someone asks to follow a locked member, from this server or another.
  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFollowRequested(FollowRequestedEvent event) {
    recordUseCase.record(
        event.targetUserId(), NotificationType.FOLLOW_REQUEST, event.follower(), null);
  }

  // A follow request's notification goes once the request is approved, turned down or withdrawn,
  // as on Mastodon.
  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onSettled(FollowRequestSettledEvent event) {
    notifications.deleteFollowRequest(event.targetUserId(), event.follower());
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onUnlocked(AccountUnlockedEvent event) {
    notifications.deleteFollowRequests(event.userId());
  }
}
