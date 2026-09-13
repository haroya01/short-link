package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.NotificationEntity;
import java.time.Instant;
import java.util.List;

public interface NotificationRepository {

  NotificationEntity save(NotificationEntity notification);

  /**
   * Newest-first page for one recipient. {@code beforeId} is an exclusive cursor (null = newest);
   * {@code limit} bounds the row count. Callers fetch {@code limit + 1} to detect a further page.
   */
  List<NotificationEntity> findPageForRecipient(Long recipientUserId, Long beforeId, int limit);

  long countUnread(Long recipientUserId);

  /** Marks one notification read iff it belongs to the recipient and is unread. */
  void markRead(Long id, Long recipientUserId, Instant at);

  /** Returns the number of unread notifications updated for the recipient. */
  int markAllRead(Long recipientUserId, Instant at);
}
