package com.example.short_link.notification.application.write;

import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import java.time.Clock;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarkNotificationReadUseCase {

  private static final Set<NotificationType> MENTIONS = NotificationType.mentionTypes();

  private final NotificationRepository repository;
  private final Clock clock;

  @Transactional
  public void markRead(Long recipientUserId, Long notificationId) {
    repository.markRead(notificationId, recipientUserId, clock.instant());
  }

  @Transactional
  public int markAllRead(Long recipientUserId) {
    return repository.markAllRead(recipientUserId, clock.instant());
  }

  @Transactional
  public int markMentionsRead(Long recipientUserId) {
    return repository.markAllReadOfTypes(recipientUserId, MENTIONS, clock.instant());
  }
}
