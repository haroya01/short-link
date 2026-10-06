package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationGroup;
import com.example.short_link.notification.domain.NotificationGroupActor;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository {

  NotificationEntity save(NotificationEntity notification);

  List<NotificationGroup> findGroupPage(Long recipientUserId, Long beforeId, int limit);

  List<NotificationGroupActor> recentActors(
      Long recipientUserId, Collection<String> groupKeys, int perGroup);

  boolean existsInGroup(
      Long recipientUserId, String groupKey, Long actorUserId, Long actorRemoteId);

  long countUnread(Long recipientUserId);

  void markRead(Long id, Long recipientUserId, Instant at);

  int markAllRead(Long recipientUserId, Instant at);
}
