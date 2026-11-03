package com.example.short_link.notification.application.write;

import com.example.short_link.common.event.Actor;
import com.example.short_link.common.user.SilenceCheck;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.notification.application.NotificationTargetCodec;
import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.dto.NotificationNoteRef;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.dto.NotificationSeriesRef;
import com.example.short_link.notification.application.dto.NotificationTarget;
import com.example.short_link.notification.application.preference.BlogNotificationPreferenceService;
import com.example.short_link.notification.application.push.NotificationPushDelivery;
import com.example.short_link.notification.application.push.PushActor;
import com.example.short_link.notification.application.push.PushApp;
import com.example.short_link.notification.application.push.PushRoute;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.application.push.PushSpeech;
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
import com.example.short_link.notification.domain.repository.NotificationRoomReader;
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
  private final UserBlockChecker blocks;
  private final NotificationRoomReader rooms;
  private final Clock clock;

  private record Conversation(String threadId, Long roomId) {}

  // A recipient who silences the actor, their server or the conversation hears nothing of it.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      Long recipientUserId, NotificationType type, Actor actor, NotificationTarget payload) {
    if (!silenced(SilenceCheck.of(recipientUserId, actor))) {
      write(recipientUserId, type, actor, payload, null, conversation(payload));
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      Long recipientUserId,
      NotificationType type,
      Actor actor,
      NotificationTarget payload,
      String groupKey) {
    if (!silenced(SilenceCheck.of(recipientUserId, actor))) {
      write(recipientUserId, type, actor, payload, groupKey, conversation(payload));
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      Long recipientUserId,
      NotificationType type,
      Actor actor,
      NotificationTarget payload,
      String groupKey,
      Long conversationId,
      Long roomId) {
    if (!silenced(SilenceCheck.of(recipientUserId, actor).inConversation(conversationId))) {
      write(
          recipientUserId,
          type,
          actor,
          payload,
          groupKey,
          conversation(conversationId, roomId, payload));
    }
  }

  private boolean silenced(SilenceCheck check) {
    return !check.recipientId().equals(check.actor().userId()) && blocks.silences(check);
  }

  // A grouped notification is written once per actor: liking, unliking and liking again adds no
  // second row or push to the same group. A notice someone caused first passes the recipient's
  // notification policy, read together with the type preference in one statement: dropped, kept
  // aside without a push, or delivered.
  private void write(
      Long recipientUserId,
      NotificationType type,
      Actor actor,
      NotificationTarget payload,
      String groupKey,
      Conversation conversation) {
    NotificationPolicyLevel verdict = verdict(recipientUserId, type, actor, payload);
    if (verdict == NotificationPolicyLevel.DROP) {
      return;
    }
    if (groupKey != null
        && repository.existsInGroup(recipientUserId, groupKey, actor.userId(), actor.remoteId())) {
      return;
    }
    String json = targetCodec.encode(payload);
    boolean filtered = verdict == NotificationPolicyLevel.FILTER;
    KeywordVerdict keywords =
        keywords(List.of(recipientUserId), type, actor, payload)
            .getOrDefault(recipientUserId, KeywordVerdict.CLEAN);
    boolean hidden = keywords == KeywordVerdict.HIDE;
    NotificationEntity notice =
        new NotificationEntity(
            recipientUserId,
            type,
            actor.userId(),
            actor.remoteId(),
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
    NotificationActor sender = resolve(actor);
    pushDelivery.send(
        recipientUserId,
        pushMessage(
            type,
            sender,
            payload,
            keywords == KeywordVerdict.CLEAN,
            Locale.forLanguageTag(recipient.map(NotificationUser::locale).orElse("ko")),
            recipient.map(NotificationUser::username).orElse(null),
            shownTo(recipientUserId, sender),
            conversation));
  }

  private static NotificationActor shownTo(Long recipientUserId, NotificationActor sender) {
    return sender != null && recipientUserId.equals(sender.userId()) ? null : sender;
  }

  private Map<Long, KeywordVerdict> keywords(
      List<Long> recipientUserIds, NotificationType type, Actor actor, NotificationTarget payload) {
    String text = othersText(type, payload);
    List<Long> others = recipientUserIds.stream().filter(id -> !id.equals(actor.userId())).toList();
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
    return switch (type.keywordText()) {
      case SOURCE_EXCERPT -> note.sourceExcerpt();
      case EXCERPT -> note.excerpt();
      case NONE -> null;
    };
  }

  private NotificationPolicyLevel verdict(
      Long recipientUserId, NotificationType type, Actor actor, NotificationTarget payload) {
    if (!type.filterable() || actor.isNone()) {
      return preferenceService.isEnabled(recipientUserId, type)
          ? NotificationPolicyLevel.ACCEPT
          : NotificationPolicyLevel.DROP;
    }
    Long mentionNoteId =
        type.mayBePrivateMention() && payload instanceof NotificationNoteRef note
            ? note.noteId()
            : null;
    return NotificationPolicy.verdict(
        policies.sender(recipientUserId, type, actor.userId(), actor.remoteId(), mentionNoteId));
  }

  public void recordForEachWhoHears(
      List<Long> candidateUserIds, NotificationType type, Actor actor, NotificationTarget payload) {
    recordForEach(
        candidateUserIds.stream()
            .filter(candidate -> !silenced(SilenceCheck.of(candidate, actor)))
            .toList(),
        type,
        actor,
        payload);
  }

  // 수신 거부자를 제외하고 청크별 트랜잭션으로 연결 점유 시간을 제한한다. 수신자 수는 제한하지 않는다. 수신자는 이미 행위자를 침묵하지 않은
  // 사람들이다(SQL이 고른 팔로워·구독자·공유자).
  public void recordForEach(
      List<Long> recipientUserIds, NotificationType type, Actor actor, NotificationTarget payload) {
    if (recipientUserIds.isEmpty()) {
      return;
    }
    List<Long> enabledRecipients = preferenceService.filterEnabled(recipientUserIds, type);
    if (enabledRecipients.isEmpty()) {
      return;
    }
    String json = targetCodec.encode(payload);
    Map<Long, KeywordVerdict> keywords = keywords(enabledRecipients, type, actor, payload);
    Set<Long> hidden =
        keywords.entrySet().stream()
            .filter(entry -> entry.getValue() == KeywordVerdict.HIDE)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    Instant now = clock.instant();
    for (int i = 0; i < enabledRecipients.size(); i += FANOUT_CHUNK) {
      List<Long> chunk =
          enabledRecipients.subList(i, Math.min(i + FANOUT_CHUNK, enabledRecipients.size()));
      fanoutWriter.persistChunk(chunk, type, actor.userId(), actor.remoteId(), json, hidden, now);
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
    NotificationActor sender = byLocale.isEmpty() ? null : resolve(actor);
    Conversation conversation = conversation(payload);
    byLocale.forEach(
        (tag, byQuote) ->
            byQuote.forEach(
                (quoted, ids) -> {
                  if (!ids.isEmpty()) {
                    pushDelivery.sendToAll(
                        ids,
                        pushMessage(
                            type,
                            sender,
                            payload,
                            quoted,
                            Locale.forLanguageTag(tag),
                            null,
                            sender,
                            conversation));
                  }
                }));
  }

  private NotificationActor resolve(Actor actor) {
    if (actor.userId() != null) {
      return actorReader.resolve(Set.of(actor.userId())).get(actor.userId());
    }
    return actor.remoteId() == null
        ? null
        : actorReader.resolveRemote(Set.of(actor.remoteId())).get(actor.remoteId());
  }

  private PushSender.PushMessage pushMessage(
      NotificationType type,
      NotificationActor sender,
      NotificationTarget payload,
      boolean quoted,
      Locale locale,
      String recipientUsername,
      NotificationActor shown,
      Conversation conversation) {
    String actorUsername = sender == null || sender.userId() == null ? null : sender.username();
    String actorName = sender != null && sender.username() != null ? sender.username() : "kurl";
    String subtitle = payload == null || !quoted ? null : payload.pushSubtitle();
    Integer milestone = payload instanceof NotificationPostRef post ? post.milestone() : null;
    String body =
        messageSource.getMessage(
            "notification.push." + type.name(), new Object[] {actorName, milestone}, locale);
    return new PushSender.PushMessage(
        "kurl",
        subtitle,
        body,
        type.name(),
        null,
        PushApp.BLOG,
        route(type, actorUsername, recipientUsername, payload),
        PushActor.of(
            shown, shown == null ? null : speech(type, payload, quoted, locale, conversation)),
        conversation.threadId());
  }

  private PushSpeech speech(
      NotificationType type,
      NotificationTarget payload,
      boolean quoted,
      Locale locale,
      Conversation conversation) {
    if (!type.inMentions()) {
      return null;
    }
    String said =
        quoted && payload instanceof NotificationNoteRef note ? note.pushSubtitle() : null;
    String text =
        said != null && !said.isBlank() ? said : pushText("notification.push.text.", type, locale);
    return new PushSpeech(text, context(type, payload, locale, conversation));
  }

  private String context(
      NotificationType type, NotificationTarget payload, Locale locale, Conversation conversation) {
    Optional<String> room =
        conversation.roomId() == null ? Optional.empty() : rooms.name(conversation.roomId());
    if (room.isPresent()) {
      return room.get();
    }
    return payload instanceof NotificationPostRef post && post.title() != null
        ? post.title()
        : pushText("notification.push.context.", type, locale);
  }

  private String pushText(String prefix, NotificationType type, Locale locale) {
    return messageSource.getMessage(prefix + type.name(), null, locale);
  }

  private static Conversation conversation(NotificationTarget payload) {
    return new Conversation(thread(payload), null);
  }

  private static Conversation conversation(
      Long conversationId, Long roomId, NotificationTarget payload) {
    if (roomId != null) {
      return new Conversation(thread("room", roomId), roomId);
    }
    return conversationId != null
        ? new Conversation(thread("note", conversationId), null)
        : conversation(payload);
  }

  private static String thread(NotificationTarget payload) {
    return switch (payload) {
      case NotificationPostRef post -> thread("post", post.postId());
      case NotificationSeriesRef series -> thread("series", series.seriesId());
      case NotificationCollectionRef collection -> thread("collection", collection.collectionId());
      case NotificationNoteRef note -> thread("note", note.noteId());
      case null -> null;
    };
  }

  private static String thread(String kind, Long id) {
    return id == null ? null : kind + ":" + id;
  }

  private static PushRoute route(
      NotificationType type,
      String actorUsername,
      String recipientUsername,
      NotificationTarget payload) {
    String owner = type.opensTheActorsWork() ? actorUsername : recipientUsername;
    return switch (payload) {
      case NotificationPostRef post ->
          PushRoute.toPost(
              actorUsername,
              post.authorUsername() != null ? post.authorUsername() : owner,
              post.slug(),
              post.commentId(),
              post.highlightId());
      case NotificationSeriesRef series -> PushRoute.toSeries(actorUsername, owner, series.slug());
      case NotificationCollectionRef collection ->
          PushRoute.toCollection(actorUsername, collection.collectionId());
      case NotificationNoteRef note ->
          note.sourceNoteId() != null
              ? PushRoute.toNote(actorUsername, actorUsername, note.sourceNoteId())
              : PushRoute.toNote(actorUsername, owner, note.noteId());
      case null -> PushRoute.toActor(actorUsername);
    };
  }
}
