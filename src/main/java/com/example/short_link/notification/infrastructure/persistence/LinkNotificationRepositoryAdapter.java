package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.LinkNotificationEntity;
import com.example.short_link.notification.domain.repository.LinkNotificationRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * {@link LinkNotificationRepository} 포트를 Spring Data 로 잇는다 — 블로그 NotificationRepositoryAdapter 와 동일
 * 결.
 */
@Component
public class LinkNotificationRepositoryAdapter implements LinkNotificationRepository {
  private final JpaLinkNotificationRepository jpa;

  public LinkNotificationRepositoryAdapter(JpaLinkNotificationRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public LinkNotificationEntity save(LinkNotificationEntity notification) {
    return jpa.save(notification);
  }

  @Override
  public List<LinkNotificationEntity> findPageForRecipient(
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
