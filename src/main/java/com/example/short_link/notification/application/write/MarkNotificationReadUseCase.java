package com.example.short_link.notification.application.write;

import com.example.short_link.notification.domain.repository.NotificationRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Marks notifications read — one by id (ownership-checked) or all unread for the recipient. */
@Service
@RequiredArgsConstructor
public class MarkNotificationReadUseCase {

  private final NotificationRepository repository;

  @Transactional
  public void markRead(Long recipientUserId, Long notificationId) {
    repository.markRead(notificationId, recipientUserId, Instant.now());
  }

  @Transactional
  public int markAllRead(Long recipientUserId) {
    return repository.markAllRead(recipientUserId, Instant.now());
  }
}
