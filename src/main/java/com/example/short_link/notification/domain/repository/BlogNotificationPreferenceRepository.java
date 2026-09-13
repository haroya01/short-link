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

  /** Atomically creates or updates one preference; concurrent writes retain exactly one row. */
  void setEnabled(Long userId, NotificationType type, boolean enabled);

  /** 후보 중 명시적으로 수신을 거부한 사용자만 일괄 반환한다. 설정이 없는 사용자는 제외한다. */
  List<Long> findDisabledUserIds(Collection<Long> userIds, NotificationType type);
}
