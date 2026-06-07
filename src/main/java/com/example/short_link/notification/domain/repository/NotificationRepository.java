package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.NotificationEntity;
import java.time.Instant;
import java.util.List;

/** Persistence port for a recipient's in-app notifications. */
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

  /** Marks every unread notification for the recipient read; returns the number updated. */
  int markAllRead(Long recipientUserId, Instant at);
}
