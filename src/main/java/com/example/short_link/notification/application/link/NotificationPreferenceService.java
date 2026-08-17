package com.example.short_link.notification.application.link;

import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationPreferenceEntity;
import com.example.short_link.notification.domain.repository.NotificationPreferenceRepository;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read + toggle link-notification opt-outs. Absent preference = enabled (default on). */
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

  /**
   * Full map for the settings screen — every opt-out-able type, defaulting absent rows to enabled.
   * {@code WARNING} is left out: operator notices are not a preference.
   */
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
    repository
        .findByUserIdAndType(userId, type)
        .ifPresentOrElse(
            row -> row.setEnabled(enabled),
            () -> repository.save(new NotificationPreferenceEntity(userId, type, enabled)));
  }
}
