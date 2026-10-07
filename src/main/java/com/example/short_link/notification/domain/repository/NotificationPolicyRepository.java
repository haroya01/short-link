package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.policy.NotificationPolicy;
import com.example.short_link.notification.domain.policy.NotificationSender;
import java.util.Optional;

public interface NotificationPolicyRepository {

  Optional<NotificationPolicy> find(Long userId);

  void save(Long userId, NotificationPolicy policy);

  // The preference for the type, the policy and the sender facts, in one statement.
  NotificationSender sender(
      Long recipientUserId,
      NotificationType type,
      Long actorUserId,
      Long actorRemoteId,
      Long mentionNoteId);

  void permit(Long recipientUserId, Long actorUserId, Long actorRemoteId);
}
