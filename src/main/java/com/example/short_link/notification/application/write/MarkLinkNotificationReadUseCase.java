package com.example.short_link.notification.application.write;

import com.example.short_link.notification.domain.repository.LinkNotificationRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarkLinkNotificationReadUseCase {
  private final LinkNotificationRepository repository;
  private final Clock clock;

  @Transactional
  public void markRead(Long recipientUserId, Long notificationId) {
    repository.markRead(notificationId, recipientUserId, clock.instant());
  }

  @Transactional
  public int markAllRead(Long recipientUserId) {
    return repository.markAllRead(recipientUserId, clock.instant());
  }
}
