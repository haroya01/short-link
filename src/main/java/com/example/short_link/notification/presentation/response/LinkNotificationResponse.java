package com.example.short_link.notification.presentation.response;

import com.example.short_link.notification.application.dto.LinkNotificationView;
import java.time.Instant;

/** 링크 알림 한 줄의 JSON 모양 — 앱이 shortCode 로 해당 링크 상세/통계로 이동한다. */
public record LinkNotificationResponse(
    Long id,
    String type,
    String shortCode,
    String subtitle,
    String body,
    boolean read,
    Instant createdAt) {

  public static LinkNotificationResponse from(LinkNotificationView v) {
    return new LinkNotificationResponse(
        v.id(), v.type().name(), v.shortCode(), v.subtitle(), v.body(), v.read(), v.createdAt());
  }
}
