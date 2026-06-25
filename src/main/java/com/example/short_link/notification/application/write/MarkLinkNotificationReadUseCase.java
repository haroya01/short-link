package com.example.short_link.notification.application.write;

import com.example.short_link.notification.domain.repository.LinkNotificationRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 링크 알림 읽음 처리 — 소유자 일치(recipientUserId)로만 갱신한다. */
@Service
@RequiredArgsConstructor
public class MarkLinkNotificationReadUseCase {
  private final LinkNotificationRepository repository;

  @Transactional
  public void markRead(Long recipientUserId, Long notificationId) {
    repository.markRead(notificationId, recipientUserId, Instant.now());
  }

  @Transactional
  public int markAllRead(Long recipientUserId) {
    return repository.markAllRead(recipientUserId, Instant.now());
  }
}
