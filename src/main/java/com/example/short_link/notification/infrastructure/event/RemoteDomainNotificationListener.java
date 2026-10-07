package com.example.short_link.notification.infrastructure.event;

import com.example.short_link.common.event.RemoteDomainBlockedEvent;
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
public class RemoteDomainNotificationListener {

  private final NotificationRepository notifications;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onDomainBlocked(RemoteDomainBlockedEvent event) {
    notifications.deleteFromDomain(event.userId(), event.domain());
  }
}
