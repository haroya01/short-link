package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NotificationRepositoryAdapter implements NotificationRepository {

  private final JpaNotificationRepository jpa;

  @Override
  public NotificationEntity save(NotificationEntity notification) {
    return jpa.save(notification);
  }

  @Override
  public List<NotificationEntity> findPageForRecipient(
      Long recipientUserId, Long beforeId, int limit) {
    PageRequest page = PageRequest.of(0, limit);
    return beforeId == null
        ? jpa.findByRecipientUserIdOrderByIdDesc(recipientUserId, page)
        : jpa.findByRecipientUserIdAndIdLessThanOrderByIdDesc(recipientUserId, beforeId, page);
  }

  @Override
  public long countUnread(Long recipientUserId) {
    return jpa.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
  }

  @Override
  public void markRead(Long id, Long recipientUserId, Instant at) {
    jpa.markRead(id, recipientUserId, at);
  }

  @Override
  public int markAllRead(Long recipientUserId, Instant at) {
    return jpa.markAllRead(recipientUserId, at);
  }
}
