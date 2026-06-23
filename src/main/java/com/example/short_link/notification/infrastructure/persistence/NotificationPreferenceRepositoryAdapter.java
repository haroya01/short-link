package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationPreferenceEntity;
import com.example.short_link.notification.domain.repository.NotificationPreferenceRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationPreferenceRepositoryAdapter implements NotificationPreferenceRepository {

  private final JpaNotificationPreferenceRepository jpa;

  @Override
  public Optional<NotificationPreferenceEntity> findByUserIdAndType(
      Long userId, LinkNotificationType type) {
    return jpa.findByUserIdAndType(userId, type);
  }

  @Override
  public List<NotificationPreferenceEntity> findByUserId(Long userId) {
    return jpa.findByUserId(userId);
  }

  @Override
  public NotificationPreferenceEntity save(NotificationPreferenceEntity preference) {
    return jpa.save(preference);
  }
}
