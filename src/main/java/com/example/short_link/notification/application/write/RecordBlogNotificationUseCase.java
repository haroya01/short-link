package com.example.short_link.notification.application.write;

import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persists a single in-app notification. Called from the after-commit interaction listener, so it
 * opens its own transaction ({@code REQUIRES_NEW}) — the originating interaction has already
 * committed by then. The post reference, when present, is serialized into the row's JSON payload.
 */
@Service
@RequiredArgsConstructor
public class RecordBlogNotificationUseCase {

  private final NotificationRepository repository;
  private final JsonMapper jsonMapper;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      Long recipientUserId, NotificationType type, Long actorUserId, NotificationPostRef post) {
    String payload = post == null ? null : jsonMapper.writeValueAsString(post);
    repository.save(new NotificationEntity(recipientUserId, type, actorUserId, payload));
  }
}
