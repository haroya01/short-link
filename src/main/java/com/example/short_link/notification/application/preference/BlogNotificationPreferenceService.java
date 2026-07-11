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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read + toggle blog-bell notification opt-outs. Absent preference = enabled (default on). */
@Service
@RequiredArgsConstructor
public class BlogNotificationPreferenceService {

  private final BlogNotificationPreferenceRepository repository;

  /** Whether {@code userId} still receives {@code type}. Absent preference = enabled. */
  @Transactional(readOnly = true)
  public boolean isEnabled(Long userId, NotificationType type) {
    return repository
        .findByUserIdAndType(userId, type)
        .map(BlogNotificationPreferenceEntity::isEnabled)
        .orElse(true);
  }

  /** Full map for the settings screen — every type, defaulting absent rows to enabled. */
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

  /**
   * Upsert the opt-out for one (user, type). Find-then-insert races two concurrent toggles (e.g. a
   * double-tap) onto the same absent row: both miss the read, both insert, and the second trips the
   * {@code (user_id, type)} unique key. We catch that and re-read to update in place, so the loser
   * settles on the winner's row instead of surfacing a 500.
   */
  @Transactional
  public void setEnabled(Long userId, NotificationType type, boolean enabled) {
    repository
        .findByUserIdAndType(userId, type)
        .ifPresentOrElse(
            row -> row.setEnabled(enabled), () -> insertOrUpdate(userId, type, enabled));
  }

  private void insertOrUpdate(Long userId, NotificationType type, boolean enabled) {
    try {
      repository.save(new BlogNotificationPreferenceEntity(userId, type, enabled));
    } catch (DataIntegrityViolationException raced) {
      // A concurrent insert won the unique key — settle on its row.
      repository.findByUserIdAndType(userId, type).ifPresent(row -> row.setEnabled(enabled));
    }
  }

  /**
   * The subset of {@code recipientUserIds} who still receive {@code type}, preserving order and
   * duplicates. Drops only those with an explicit opt-out; a single bulk query resolves the whole
   * candidate set so a NEW_POST fan-out never runs one lookup per follower.
   */
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
