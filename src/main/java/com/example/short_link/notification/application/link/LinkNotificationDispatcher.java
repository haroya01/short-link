package com.example.short_link.notification.application.link;

import com.example.short_link.notification.application.push.NotificationPushDelivery;
import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.domain.LinkNotificationEntity;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.repository.LinkNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** shortCode가 null이면 다이제스트처럼 특정 링크에 속하지 않는 알림이다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkNotificationDispatcher {

  private final NotificationPreferenceService preferences;
  private final NotificationPushDelivery pushDelivery;
  private final LinkNotificationRepository repository;

  public void dispatch(
      Long userId, LinkNotificationType type, String shortCode, String subtitle, String body) {
    if (userId == null) {
      return;
    }
    // 인박스 기록은 푸시 설정과 무관 — 푸시를 꺼도 인앱 목록엔 남는다.
    repository.save(new LinkNotificationEntity(userId, type, shortCode, subtitle, body));
    // 운영자 경고는 정책 통지라 옵트아웃 대상이 아니다 — 설정과 무관하게 푸시까지 나간다.
    if (type != LinkNotificationType.WARNING && !preferences.isEnabled(userId, type)) {
      return;
    }
    pushDelivery.send(
        userId, new PushSender.PushMessage("kurl", subtitle, body, type.name(), shortCode));
  }
}
