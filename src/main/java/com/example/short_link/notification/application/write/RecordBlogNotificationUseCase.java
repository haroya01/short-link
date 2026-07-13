package com.example.short_link.notification.application.write;

import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.preference.BlogNotificationPreferenceService;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
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

  private static final int FANOUT_CHUNK = 500;

  private final NotificationRepository repository;
  private final JsonMapper jsonMapper;
  private final PushSender pushSender;
  private final UserRepository userRepository;
  private final MessageSource messageSource;
  private final BlogNotificationPreferenceService preferenceService;
  private final NotificationFanoutWriter fanoutWriter;

  /**
   * A single notification. {@code payload} is the type's target ref (post / series) or null.
   * Skipped entirely — no bell row, no push — when the recipient has muted this type; an absent
   * preference is on, so existing behavior is unchanged.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      Long recipientUserId, NotificationType type, Long actorUserId, Object payload) {
    if (!preferenceService.isEnabled(recipientUserId, type)) {
      return;
    }
    String json = payload == null ? null : jsonMapper.writeValueAsString(payload);
    repository.save(new NotificationEntity(recipientUserId, type, actorUserId, json));
    pushSender.send(
        recipientUserId, pushMessage(type, actorUserId, payload, localeOf(recipientUserId)));
  }

  /**
   * Fan-out: one row per recipient sharing the same type/actor/target — a followed author's new
   * post landing in every follower's bell, or a grown path reaching each of its prior contributors.
   * The target ref ({@link NotificationPostRef} or {@link NotificationCollectionRef}) is serialized
   * once and reused across the inserts. Recipients who muted this type are dropped up front by one
   * bulk query (no per-follower lookup); an absent preference is on, so a follower who never
   * touched settings still receives it.
   *
   * <p>Runs off the request thread (the caller is an after-commit async listener); the surviving
   * recipients are chunked into separate transactions so a popular author's fan-out never holds one
   * DB connection open across the whole batch (the 2026-06 pool-exhaustion failure mode). Every
   * enabled follower is still notified — chunking bounds connection-hold, it does not cap the
   * audience.
   */
  public void recordForEach(
      List<Long> recipientUserIds, NotificationType type, Long actorUserId, Object payload) {
    if (recipientUserIds.isEmpty()) {
      return;
    }
    List<Long> enabledRecipients = preferenceService.filterEnabled(recipientUserIds, type);
    if (enabledRecipients.isEmpty()) {
      return;
    }
    String json = payload == null ? null : jsonMapper.writeValueAsString(payload);
    for (int i = 0; i < enabledRecipients.size(); i += FANOUT_CHUNK) {
      List<Long> chunk =
          enabledRecipients.subList(i, Math.min(i + FANOUT_CHUNK, enabledRecipients.size()));
      fanoutWriter.persistChunk(chunk, type, actorUserId, json);
    }
    // 수신자를 로케일별로 묶어 각 언어로 푸시 — 한 번의 조합을 그 로케일 그룹에 보낸다.
    Map<String, List<Long>> byLocale =
        userRepository.findAllByIdIn(enabledRecipients).stream()
            .collect(
                Collectors.groupingBy(
                    RecordBlogNotificationUseCase::localeTag,
                    Collectors.mapping(UserEntity::getId, Collectors.toList())));
    byLocale.forEach(
        (tag, ids) ->
            pushSender.sendToAll(
                ids, pushMessage(type, actorUserId, payload, Locale.forLanguageTag(tag))));
  }

  /** 앱 벨과 같은 문구를 수신자 로케일로 — MessageSource 번들(messages_*.properties)에서 렌더한다. */
  private PushSender.PushMessage pushMessage(
      NotificationType type, Long actorUserId, Object payload, Locale locale) {
    String actor =
        userRepository
            .findById(actorUserId)
            .map(user -> user.getUsername() == null ? "kurl" : user.getUsername())
            .orElse("kurl");
    String subtitle = subtitleOf(payload);
    String body =
        messageSource.getMessage("notification.push." + type.name(), new Object[] {actor}, locale);
    return new PushSender.PushMessage("kurl", subtitle, body);
  }

  /** The push subtitle: a post's title, or a collection's name for a graph notice, else none. */
  private static String subtitleOf(Object payload) {
    if (payload instanceof NotificationPostRef ref && ref.title() != null) {
      return ref.title();
    }
    if (payload instanceof NotificationCollectionRef ref && ref.collectionName() != null) {
      return ref.collectionName();
    }
    return null;
  }

  /** 수신자의 저장된 선호 로케일(모르면 ko). */
  private Locale localeOf(Long recipientUserId) {
    return Locale.forLanguageTag(
        userRepository.findById(recipientUserId).map(UserEntity::getLocale).orElse("ko"));
  }

  private static String localeTag(UserEntity user) {
    return user.getLocale() == null ? "ko" : user.getLocale();
  }
}
