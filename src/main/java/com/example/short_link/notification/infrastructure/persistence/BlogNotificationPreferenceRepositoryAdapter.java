package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.BlogNotificationPreferenceEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.BlogNotificationPreferenceRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BlogNotificationPreferenceRepositoryAdapter
    implements BlogNotificationPreferenceRepository {

  private final JpaBlogNotificationPreferenceRepository jpa;

  @Override
  public Optional<BlogNotificationPreferenceEntity> findByUserIdAndType(
      Long userId, NotificationType type) {
    return jpa.findByUserIdAndType(userId, type);
  }

  @Override
  public List<BlogNotificationPreferenceEntity> findByUserId(Long userId) {
    return jpa.findByUserId(userId);
  }

  @Override
  public BlogNotificationPreferenceEntity save(BlogNotificationPreferenceEntity preference) {
    return jpa.save(preference);
  }

  @Override
  public List<Long> findDisabledUserIds(Collection<Long> userIds, NotificationType type) {
    if (userIds.isEmpty()) {
      return List.of();
    }
    return jpa.findDisabledUserIds(userIds, type);
  }
}
