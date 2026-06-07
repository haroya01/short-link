package com.example.short_link.notification.application.write;

import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persists in-app notifications. Called from after-commit listeners, so it opens its own
 * transaction ({@code REQUIRES_NEW}) — the originating action has already committed by then. The
 * target reference, when present, is serialized into the row's JSON payload.
 */
@Service
@RequiredArgsConstructor
public class RecordBlogNotificationUseCase {

  private final NotificationRepository repository;
  private final JsonMapper jsonMapper;

  /** A single notification. {@code payload} is the type's target ref (post / series) or null. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      Long recipientUserId, NotificationType type, Long actorUserId, Object payload) {
    String json = payload == null ? null : jsonMapper.writeValueAsString(payload);
    repository.save(new NotificationEntity(recipientUserId, type, actorUserId, json));
  }

  /**
   * Fan-out: one row per recipient sharing the same type/actor/post — a followed author's new post
   * landing in every follower's bell. The post ref is serialized once and reused across the
   * inserts.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordForEach(
      List<Long> recipientUserIds,
      NotificationType type,
      Long actorUserId,
      NotificationPostRef post) {
    if (recipientUserIds.isEmpty()) {
      return;
    }
    String json = post == null ? null : jsonMapper.writeValueAsString(post);
    for (Long recipientUserId : recipientUserIds) {
      repository.save(new NotificationEntity(recipientUserId, type, actorUserId, json));
    }
  }
}
