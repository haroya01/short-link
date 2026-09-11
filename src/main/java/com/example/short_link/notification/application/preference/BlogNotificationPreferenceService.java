package com.example.short_link.notification.application.preference;

import com.example.short_link.notification.domain.BlogNotificationPreferenceEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.BlogNotificationPreferenceRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 저장된 설정이 없으면 알림을 허용한다. */
@Service
@RequiredArgsConstructor
public class BlogNotificationPreferenceService {

  private final BlogNotificationPreferenceRepository repository;

  @Transactional(readOnly = true)
  public boolean isEnabled(Long userId, NotificationType type) {
    return repository
        .findByUserIdAndType(userId, type)
        .map(BlogNotificationPreferenceEntity::isEnabled)
        .orElse(true);
  }

  @Transactional(readOnly = true)
  public Map<NotificationType, Boolean> all(Long userId) {
    Map<NotificationType, Boolean> result = new EnumMap<>(NotificationType.class);
    for (NotificationType type : NotificationType.values()) {
      result.put(type, true);
    }
    for (BlogNotificationPreferenceEntity row : repository.findByUserId(userId)) {
      result.put(row.getType(), row.isEnabled());
    }
    return result;
  }

  /** 설정이 없는 경우를 포함해 동일 사용자·유형의 동시 변경을 저장소에서 직렬화한다. */
  @Transactional
  public void setEnabled(Long userId, NotificationType type, boolean enabled) {
    repository.setEnabled(userId, type, enabled);
  }

  /** 명시적 수신 거부만 일괄 제외하며 입력 순서와 중복은 유지한다. */
  @Transactional(readOnly = true)
  public List<Long> filterEnabled(List<Long> recipientUserIds, NotificationType type) {
    if (recipientUserIds.isEmpty()) {
      return recipientUserIds;
    }
    Set<Long> disabled = new HashSet<>(repository.findDisabledUserIds(recipientUserIds, type));
    if (disabled.isEmpty()) {
      return recipientUserIds;
    }
    List<Long> enabled = new ArrayList<>(recipientUserIds.size());
    for (Long id : recipientUserIds) {
      if (!disabled.contains(id)) {
        enabled.add(id);
      }
    }
    return enabled;
  }
}
