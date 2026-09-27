package com.example.short_link.notification.presentation.response;

import com.example.short_link.notification.application.dto.LinkNotificationView;
import java.time.Instant;

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
