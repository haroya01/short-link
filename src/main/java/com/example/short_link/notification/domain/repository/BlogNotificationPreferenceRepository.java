package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.BlogNotificationPreferenceEntity;
import com.example.short_link.notification.domain.NotificationType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BlogNotificationPreferenceRepository {

  Optional<BlogNotificationPreferenceEntity> findByUserIdAndType(
      Long userId, NotificationType type);

  List<BlogNotificationPreferenceEntity> findByUserId(Long userId);

  void setEnabled(Long userId, NotificationType type, boolean enabled);

  List<Long> findDisabledUserIds(Collection<Long> userIds, NotificationType type);
}
