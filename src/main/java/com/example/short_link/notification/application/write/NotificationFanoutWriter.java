package com.example.short_link.notification.application.write;

import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one fan-out chunk in its own transaction. Split out of {@link
 * RecordBlogNotificationUseCase} so each chunk acquires and releases a DB connection independently
 * — a popular author's thousand-follower fan-out then never holds a single connection open across
 * the whole batch (the 2026-06 pool-exhaustion failure mode).
 */
@Service
@RequiredArgsConstructor
public class NotificationFanoutWriter {

  private final NotificationRepository repository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void persistChunk(
      List<Long> recipientUserIds, NotificationType type, Long actorUserId, String json) {
    for (Long recipientUserId : recipientUserIds) {
      repository.save(new NotificationEntity(recipientUserId, type, actorUserId, json));
    }
  }
}
