package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.BlogNotificationPreferenceEntity;
import com.example.short_link.notification.domain.NotificationType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence port for per-user blog-bell notification opt-outs. */
public interface BlogNotificationPreferenceRepository {

  Optional<BlogNotificationPreferenceEntity> findByUserIdAndType(
      Long userId, NotificationType type);

  List<BlogNotificationPreferenceEntity> findByUserId(Long userId);

  BlogNotificationPreferenceEntity save(BlogNotificationPreferenceEntity preference);

  /**
   * Of the given candidate user ids, those who have opted OUT of {@code type} (an explicit {@code
   * enabled=false} row). One query for the whole set so a NEW_POST fan-out can drop silenced
   * followers without an N+1 per follower. Absent candidates are enabled by default and never
   * returned.
   */
  List<Long> findDisabledUserIds(Collection<Long> userIds, NotificationType type);
}
