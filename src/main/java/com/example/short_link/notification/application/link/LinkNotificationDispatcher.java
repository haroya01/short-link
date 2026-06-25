package com.example.short_link.notification.application.link;

import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.domain.LinkNotificationEntity;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.repository.LinkNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 링크 알림 한 건을 처리한다 — 인박스에 항상 기록하고(푸시를 꺼 둬도 앱에서 본다), 푸시 설정이 켜져 있으면 푸시도 보낸다. {@code shortCode} 는 어떤
 * 링크의 알림인지 가리킨다(다이제스트처럼 링크 단위가 아니면 {@code null}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkNotificationDispatcher {

  private final NotificationPreferenceService preferences;
  private final PushSender pushSender;
  private final LinkNotificationRepository repository;

  public void dispatch(
      Long userId, LinkNotificationType type, String shortCode, String subtitle, String body) {
    if (userId == null) {
      return;
    }
    // 인박스 기록은 푸시 설정과 무관 — 푸시를 꺼도 인앱 목록엔 남는다.
    repository.save(new LinkNotificationEntity(userId, type, shortCode, subtitle, body));
    if (!preferences.isEnabled(userId, type)) {
      return;
    }
    pushSender.send(userId, new PushSender.PushMessage("kurl", subtitle, body));
  }
}
