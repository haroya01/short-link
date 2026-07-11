package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.BlogNotificationPreferenceEntity;
import com.example.short_link.notification.domain.NotificationType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaBlogNotificationPreferenceRepository
    extends JpaRepository<BlogNotificationPreferenceEntity, Long> {

  Optional<BlogNotificationPreferenceEntity> findByUserIdAndType(
      Long userId, NotificationType type);

  List<BlogNotificationPreferenceEntity> findByUserId(Long userId);

  /** User ids in the candidate set who explicitly opted out of {@code type} — one query, no N+1. */
  @Query(
      "select p.userId from BlogNotificationPreferenceEntity p "
          + "where p.type = :type and p.enabled = false and p.userId in :userIds")
  List<Long> findDisabledUserIds(
      @Param("userIds") Collection<Long> userIds, @Param("type") NotificationType type);
}
