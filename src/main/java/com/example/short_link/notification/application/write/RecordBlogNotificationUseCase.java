package com.example.short_link.notification.application.write;

import com.example.short_link.notification.application.NotificationTargetCodec;
import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.dto.NotificationNoteRef;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.dto.NotificationSeriesRef;
import com.example.short_link.notification.application.dto.NotificationTarget;
import com.example.short_link.notification.application.preference.BlogNotificationPreferenceService;
import com.example.short_link.notification.application.push.NotificationPushDelivery;
import com.example.short_link.notification.application.push.PushApp;
import com.example.short_link.notification.application.push.PushRoute;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.NotificationUser;
import com.example.short_link.notification.domain.policy.KeywordVerdict;
import com.example.short_link.notification.domain.policy.NotificationPolicy;
import com.example.short_link.notification.domain.policy.NotificationPolicyLevel;
import com.example.short_link.notification.domain.repository.NotificationActorReader;
import com.example.short_link.notification.domain.repository.NotificationKeywordFilterReader;
import com.example.short_link.notification.domain.repository.NotificationPolicyRepository;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import com.example.short_link.notification.domain.repository.NotificationUserReader;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 원래 작업의 커밋 이후 별도 트랜잭션으로 알림을 저장한다. 푸시는 알림 저장 트랜잭션까지 커밋된 뒤 제출한다.
@Service
@RequiredArgsConstructor
public class RecordBlogNotificationUseCase {

  private static final int FANOUT_CHUNK = 500;

  private final NotificationRepository repository;
  private final NotificationTargetCodec targetCodec;
  private final NotificationPushDelivery pushDelivery;
  private final NotificationUserReader userReader;
  private final MessageSource messageSource;
  private final BlogNotificationPreferenceService preferenceService;
  private final NotificationFanoutWriter fanoutWriter;
  private final NotificationActorReader actorReader;
  private final NotificationPolicyRepository policies;
  private final NotificationKeywordFilterReader keywordFilters;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      Long recipientUserId, NotificationType type, Long actorUserId, NotificationTarget payload) {
    write(recipientUserId, type, actorUserId, null, payload, null);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      Long recipientUserId,
      NotificationType type,
      Long actorUserId,
      Long actorRemoteId,
      NotificationTarget payload,
      String groupKey) {
    write(recipientUserId, type, actorUserId, actorRemoteId, payload, groupKey);
  }

  // A grouped notification is written once per actor: liking, unliking and liking again adds no
  // second row or push to the same group. A notice someone caused first passes the recipient's
  // notification policy, read together with the type preference in one statement: dropped, kept
  // aside without a push, or delivered.
  private void write(
      Long recipientUserId,
      NotificationType type,
      Long actorUserId,
      Long actorRemoteId,
      NotificationTarget payload,
      String groupKey) {
    NotificationPolicyLevel verdict =
        verdict(recipientUserId, type, actorUserId, actorRemoteId, payload);
    if (verdict == NotificationPolicyLevel.DROP) {
      return;
    }
    if (groupKey != null
        && repository.existsInGroup(recipientUserId, groupKey, actorUserId, actorRemoteId)) {
      return;
    }
    String json = targetCodec.encode(payload);
    boolean filtered = verdict == NotificationPolicyLevel.FILTER;
    KeywordVerdict keywords =
        keywords(List.of(recipientUserId), type, actorUserId, payload)
            .getOrDefault(recipientUserId, KeywordVerdict.CLEAN);
    boolean hidden = keywords == KeywordVerdict.HIDE;
    NotificationEntity notice =
        new NotificationEntity(
            recipientUserId,
            type,
            actorUserId,
            actorRemoteId,
            json,
            hidden ? null : groupKey,
            filtered);
    if (hidden) {
      notice.markRead(clock.instant());
    }
    repository.save(notice);
    if (filtered || hidden) {
      return;
    }
    Optional<NotificationUser> recipient = userReader.findById(recipientUserId);
    pushDelivery.send(
        recipientUserId,
        pushMessage(
            type,
            actorUserId,
            actorRemoteId,
            payload,
            keywords == KeywordVerdict.CLEAN,
            Locale.forLanguageTag(recipient.map(NotificationUser::locale).orElse("ko")),
            recipient.map(NotificationUser::username).orElse(null)));
  }

  private Map<Long, KeywordVerdict> keywords(
      List<Long> recipientUserIds,
      NotificationType type,
      Long actorUserId,
      NotificationTarget payload) {
    String text = othersText(type, payload);
    List<Long> others = recipientUserIds.stream().filter(id -> !id.equals(actorUserId)).toList();
    if (text == null || text.isBlank() || others.isEmpty()) {
      return Map.of();
    }
    Map<Long, KeywordVerdict> verdicts = new HashMap<>();
    keywordFilters
        .activeFor(others, clock.instant())
        .forEach(
            (recipientUserId, filters) ->
                verdicts.put(recipientUserId, KeywordVerdict.of(filters, text)));
    return verdicts;
  }

  private static String othersText(NotificationType type, NotificationTarget payload) {
    if (!(payload instanceof NotificationNoteRef note)) {
      return null;
    }
    return switch (type) {
      case NOTE_REPLY, NOTE_QUOTE -> note.sourceExcerpt();
      case NOTE_MENTION, POST_QUOTE, NOTE_POST, NOTE_EDIT, NOTE_POLL -> note.excerpt();
      default -> null;
    };
  }

  private NotificationPolicyLevel verdict(
      Long recipientUserId,
      NotificationType type,
      Long actorUserId,
      Long actorRemoteId,
      NotificationTarget payload) {
    if (!type.filterable() || (actorUserId == null && actorRemoteId == null)) {
      return preferenceService.isEnabled(recipientUserId, type)
          ? NotificationPolicyLevel.ACCEPT
          : NotificationPolicyLevel.DROP;
    }
    Long mentionNoteId =
        type == NotificationType.NOTE_MENTION && payload instanceof NotificationNoteRef note
            ? note.noteId()
            : null;
    return NotificationPolicy.verdict(
        policies.sender(recipientUserId, type, actorUserId, actorRemoteId, mentionNoteId));
  }

  // 수신 거부자를 제외하고 청크별 트랜잭션으로 연결 점유 시간을 제한한다. 수신자 수는 제한하지 않는다.
  public void recordForEach(
      List<Long> recipientUserIds,
      NotificationType type,
      Long actorUserId,
      NotificationTarget payload) {
    recordForEach(recipientUserIds, type, actorUserId, null, payload);
  }

  public void recordForEach(
      List<Long> recipientUserIds,
      NotificationType type,
      Long actorUserId,
      Long actorRemoteId,
      NotificationTarget payload) {
    if (recipientUserIds.isEmpty()) {
      return;
    }
    List<Long> enabledRecipients = preferenceService.filterEnabled(recipientUserIds, type);
    if (enabledRecipients.isEmpty()) {
      return;
    }
    String json = targetCodec.encode(payload);
    Map<Long, KeywordVerdict> keywords = keywords(enabledRecipients, type, actorUserId, payload);
    Set<Long> hidden =
        keywords.entrySet().stream()
            .filter(entry -> entry.getValue() == KeywordVerdict.HIDE)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    Instant now = clock.instant();
    for (int i = 0; i < enabledRecipients.size(); i += FANOUT_CHUNK) {
      List<Long> chunk =
          enabledRecipients.subList(i, Math.min(i + FANOUT_CHUNK, enabledRecipients.size()));
      fanoutWriter.persistChunk(chunk, type, actorUserId, actorRemoteId, json, hidden, now);
    }
    Map<String, Map<Boolean, List<Long>>> byLocale =
        userReader.findAllByIdIn(enabledRecipients).stream()
            .filter(user -> !hidden.contains(user.id()))
            .collect(
                Collectors.groupingBy(
                    NotificationUser::localeTag,
                    Collectors.partitioningBy(
                        user -> keywords.get(user.id()) != KeywordVerdict.WARN,
                        Collectors.mapping(NotificationUser::id, Collectors.toList()))));
    byLocale.forEach(
        (tag, byQuote) ->
            byQuote.forEach(
                (quoted, ids) -> {
                  if (!ids.isEmpty()) {
                    pushDelivery.sendToAll(
                        ids,
                        pushMessage(
                            type,
                            actorUserId,
                            actorRemoteId,
                            payload,
                            quoted,
                            Locale.forLanguageTag(tag),
                            null));
                  }
                }));
  }

  private PushSender.PushMessage pushMessage(
      NotificationType type,
      Long actorUserId,
      Long actorRemoteId,
      NotificationTarget payload,
      boolean quoted,
      Locale locale,
      String recipientUsername) {
    String actorUsername =
        actorUserId == null
            ? null
            : userReader.findById(actorUserId).map(NotificationUser::username).orElse(null);
    String remoteHandle =
        actorRemoteId == null
            ? null
            : Optional.ofNullable(
                    actorReader.resolveRemote(Set.of(actorRemoteId)).get(actorRemoteId))
                .map(NotificationActor::username)
                .orElse(null);
    String actor =
        actorUsername != null ? actorUsername : remoteHandle != null ? remoteHandle : "kurl";
    String subtitle = payload == null || !quoted ? null : payload.pushSubtitle();
    String body =
        messageSource.getMessage("notification.push." + type.name(), new Object[] {actor}, locale);
    return new PushSender.PushMessage(
        "kurl",
        subtitle,
        body,
        type.name(),
        null,
        PushApp.BLOG,
        route(type, actorUsername, recipientUsername, payload));
  }

  private static PushRoute route(
      NotificationType type,
      String actorUsername,
      String recipientUsername,
      NotificationTarget payload) {
    return switch (payload) {
      case NotificationPostRef post ->
          new PushRoute(
              actorUsername,
              post.authorUsername() != null
                  ? post.authorUsername()
                  : type == NotificationType.NEW_POST ? actorUsername : recipientUsername,
              post.slug(),
              null,
              null,
              post.commentId(),
              post.highlightId());
      case NotificationSeriesRef series ->
          new PushRoute(actorUsername, recipientUsername, null, series.slug(), null, null, null);
      case NotificationCollectionRef collection ->
          new PushRoute(actorUsername, null, null, null, collection.collectionId(), null, null);
      case NotificationNoteRef note ->
          note.sourceNoteId() != null
              ? new PushRoute(
                  actorUsername, actorUsername, null, null, null, null, null, note.sourceNoteId())
              : new PushRoute(
                  actorUsername,
                  type == NotificationType.NOTE_POLL
                          || type == NotificationType.NOTE_POST
                          || type == NotificationType.NOTE_EDIT
                          || type == NotificationType.POST_QUOTE
                      ? actorUsername
                      : recipientUsername,
                  null,
                  null,
                  null,
                  null,
                  null,
                  note.noteId());
      case null -> new PushRoute(actorUsername, null, null, null, null, null, null);
    };
  }
}
