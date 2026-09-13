package com.example.short_link.notification.application.link;

import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationPreferenceEntity;
import com.example.short_link.notification.domain.repository.NotificationPreferenceRepository;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 저장된 설정이 없으면 알림을 허용한다. */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

  private final NotificationPreferenceRepository repository;

  @Transactional(readOnly = true)
  public boolean isEnabled(Long userId, LinkNotificationType type) {
    return repository
        .findByUserIdAndType(userId, type)
        .map(NotificationPreferenceEntity::isEnabled)
        .orElse(true);
  }

  /** 운영자 WARNING은 수신 거부 대상이 아니므로 설정 목록에서 제외한다. */
  @Transactional(readOnly = true)
  public Map<LinkNotificationType, Boolean> all(Long userId) {
    Map<LinkNotificationType, Boolean> result = new EnumMap<>(LinkNotificationType.class);
    for (LinkNotificationType type : LinkNotificationType.values()) {
      if (type == LinkNotificationType.WARNING) {
        continue;
      }
      result.put(type, true);
    }
    for (NotificationPreferenceEntity row : repository.findByUserId(userId)) {
      if (row.getType() == LinkNotificationType.WARNING) {
        continue;
      }
      result.put(row.getType(), row.isEnabled());
    }
    return result;
  }

  @Transactional
  public void setEnabled(Long userId, LinkNotificationType type, boolean enabled) {
    repository.setEnabled(userId, type, enabled);
  }
}
