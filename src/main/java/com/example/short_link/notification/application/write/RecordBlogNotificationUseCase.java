package com.example.short_link.notification.application.write;

import com.example.short_link.notification.application.NotificationTargetCodec;
import com.example.short_link.notification.application.dto.NotificationTarget;
import com.example.short_link.notification.application.preference.BlogNotificationPreferenceService;
import com.example.short_link.notification.application.push.NotificationPushDelivery;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.NotificationUser;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import com.example.short_link.notification.domain.repository.NotificationUserReader;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 원래 작업의 커밋 이후 별도 트랜잭션으로 알림을 저장한다. 푸시는 알림 저장 트랜잭션까지 커밋된 뒤 제출한다. */
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

  /** 수신 거부면 인앱 알림과 푸시 모두 생략한다. payload는 대상 참조 또는 null이다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      Long recipientUserId, NotificationType type, Long actorUserId, NotificationTarget payload) {
    if (!preferenceService.isEnabled(recipientUserId, type)) {
      return;
    }
    String json = targetCodec.encode(payload);
    repository.save(new NotificationEntity(recipientUserId, type, actorUserId, json));
    pushDelivery.send(
        recipientUserId, pushMessage(type, actorUserId, payload, localeOf(recipientUserId)));
  }

  /** 수신 거부자를 제외하고 청크별 트랜잭션으로 연결 점유 시간을 제한한다. 수신자 수는 제한하지 않는다. */
  public void recordForEach(
      List<Long> recipientUserIds,
      NotificationType type,
      Long actorUserId,
      NotificationTarget payload) {
    if (recipientUserIds.isEmpty()) {
      return;
    }
    List<Long> enabledRecipients = preferenceService.filterEnabled(recipientUserIds, type);
    if (enabledRecipients.isEmpty()) {
      return;
    }
    String json = targetCodec.encode(payload);
    for (int i = 0; i < enabledRecipients.size(); i += FANOUT_CHUNK) {
      List<Long> chunk =
          enabledRecipients.subList(i, Math.min(i + FANOUT_CHUNK, enabledRecipients.size()));
      fanoutWriter.persistChunk(chunk, type, actorUserId, json);
    }
    Map<String, List<Long>> byLocale =
        userReader.findAllByIdIn(enabledRecipients).stream()
            .collect(
                Collectors.groupingBy(
                    NotificationUser::localeTag,
                    Collectors.mapping(NotificationUser::id, Collectors.toList())));
    byLocale.forEach(
        (tag, ids) ->
            pushDelivery.sendToAll(
                ids, pushMessage(type, actorUserId, payload, Locale.forLanguageTag(tag))));
  }

  private PushSender.PushMessage pushMessage(
      NotificationType type, Long actorUserId, NotificationTarget payload, Locale locale) {
    String actor =
        userReader
            .findById(actorUserId)
            .map(user -> user.username() == null ? "kurl" : user.username())
            .orElse("kurl");
    String subtitle = payload == null ? null : payload.pushSubtitle();
    String body =
        messageSource.getMessage("notification.push." + type.name(), new Object[] {actor}, locale);
    return new PushSender.PushMessage("kurl", subtitle, body);
  }

  private Locale localeOf(Long recipientUserId) {
    return Locale.forLanguageTag(
        userReader.findById(recipientUserId).map(NotificationUser::locale).orElse("ko"));
  }
}
