package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationGroup;
import com.example.short_link.notification.domain.NotificationGroupActor;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.policy.FilteredSender;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository {

  NotificationEntity save(NotificationEntity notification);

  List<NotificationGroup> findGroupPage(Long recipientUserId, Long beforeId, int limit);

  List<NotificationGroup> findGroupPageOfTypes(
      Long recipientUserId, Collection<NotificationType> types, Long beforeId, int limit);

  List<NotificationGroupActor> recentActors(
      Long recipientUserId, Collection<String> groupKeys, int perGroup);

  boolean existsInGroup(
      Long recipientUserId, String groupKey, Long actorUserId, Long actorRemoteId);

  long countUnread(Long recipientUserId);

  void markRead(Long id, Long recipientUserId, Instant at);

  int markAllRead(Long recipientUserId, Instant at);

  int markAllReadOfTypes(Long recipientUserId, Collection<NotificationType> types, Instant at);

  int deleteFromDomain(Long recipientUserId, String domain);

  int deleteFromServer(String domain);

  int deleteFollowRequest(Long recipientUserId, Long actorUserId, Long actorRemoteId);

  int deleteFollowRequests(Long recipientUserId);

  List<FilteredSender> filteredSenders(Long recipientUserId, int limit);

  int unfilter(Long recipientUserId, Long actorUserId, Long actorRemoteId);

  int deleteFiltered(Long recipientUserId, Long actorUserId, Long actorRemoteId);
}
