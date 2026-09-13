package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.LinkNotificationEntity;
import java.time.Instant;
import java.util.List;

public interface LinkNotificationRepository {
  LinkNotificationEntity save(LinkNotificationEntity notification);

  List<LinkNotificationEntity> findPageForRecipient(Long recipientUserId, Long beforeId, int limit);

  long countUnread(Long recipientUserId);

  void markRead(Long id, Long recipientUserId, Instant at);

  int markAllRead(Long recipientUserId, Instant at);
}
