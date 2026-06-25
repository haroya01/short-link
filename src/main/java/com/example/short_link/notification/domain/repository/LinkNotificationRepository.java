package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.LinkNotificationEntity;
import java.time.Instant;
import java.util.List;

/** 링크 알림 인박스 영속성 포트 — 블로그 {@code NotificationRepository} 와 같은 모양(커서 페이지·안 읽음·읽음 처리). */
public interface LinkNotificationRepository {
  LinkNotificationEntity save(LinkNotificationEntity notification);

  List<LinkNotificationEntity> findPageForRecipient(Long recipientUserId, Long beforeId, int limit);

  long countUnread(Long recipientUserId);

  void markRead(Long id, Long recipientUserId, Instant at);

  int markAllRead(Long recipientUserId, Instant at);
}
