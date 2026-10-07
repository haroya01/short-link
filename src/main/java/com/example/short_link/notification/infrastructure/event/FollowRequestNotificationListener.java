package com.example.short_link.notification.infrastructure.event;

import com.example.short_link.common.event.AccountUnlockedEvent;
import com.example.short_link.common.event.FollowRequestSettledEvent;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// A follow request's notification goes once the request is approved, turned down or withdrawn, as
// on Mastodon.
@Component
@RequiredArgsConstructor
public class FollowRequestNotificationListener {

  private final NotificationRepository notifications;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onSettled(FollowRequestSettledEvent event) {
    notifications.deleteFollowRequest(
        event.targetUserId(), event.followerUserId(), event.followerRemoteActorId());
  }

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onUnlocked(AccountUnlockedEvent event) {
    notifications.deleteFollowRequests(event.userId());
  }
}
