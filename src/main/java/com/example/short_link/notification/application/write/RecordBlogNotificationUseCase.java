package com.example.short_link.notification.application.write;

import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import com.example.short_link.user.domain.repository.UserRepository;
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
 *
 * <p>This is also the single choke point every notification type flows through, so the APNs push
 * mirror hangs here: same recipient, same moment, fire-and-forget (the sender never blocks the
 * transaction; the in-app bell stays the source of truth).
 */
@Service
@RequiredArgsConstructor
public class RecordBlogNotificationUseCase {

  private final NotificationRepository repository;
  private final JsonMapper jsonMapper;
  private final PushSender pushSender;
  private final UserRepository userRepository;

  /** A single notification. {@code payload} is the type's target ref (post / series) or null. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      Long recipientUserId, NotificationType type, Long actorUserId, Object payload) {
    String json = payload == null ? null : jsonMapper.writeValueAsString(payload);
    repository.save(new NotificationEntity(recipientUserId, type, actorUserId, json));
    pushSender.send(recipientUserId, pushMessage(type, actorUserId, payload));
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
    pushSender.sendToAll(recipientUserIds, pushMessage(type, actorUserId, post));
  }

  /** 앱 벨과 같은 문구 — 수신자 로케일을 모르는 동안은 한국어 고정(서비스 1차 시장). */
  private PushSender.PushMessage pushMessage(
      NotificationType type, Long actorUserId, Object payload) {
    String actor =
        userRepository
            .findById(actorUserId)
            .map(user -> user.getUsername() == null ? "kurl" : user.getUsername())
            .orElse("kurl");
    String subtitle =
        payload instanceof NotificationPostRef ref && ref.title() != null ? ref.title() : null;
    String body =
        switch (type) {
          case LIKE -> actor + "님이 글을 좋아합니다";
          case COMMENT -> actor + "님이 댓글을 남겼습니다";
          case REPLY -> actor + "님이 답글을 남겼습니다";
          case FOLLOW -> actor + "님이 팔로우하기 시작했습니다";
          case SERIES_SUBSCRIBE -> actor + "님이 시리즈를 구독합니다";
          case NEW_POST -> actor + "님이 새 글을 발행했습니다";
          case MENTION -> actor + "님이 회원님을 언급했습니다";
        };
    return new PushSender.PushMessage("kurl", subtitle, body);
  }
}
