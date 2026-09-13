package com.example.short_link.notification.application;

import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.application.dto.NotificationSeriesRef;
import com.example.short_link.notification.application.dto.NotificationTarget;
import com.example.short_link.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Stored JSON target shapes have no discriminator for compatibility with existing payloads. */
@Component
@RequiredArgsConstructor
public class NotificationTargetCodec {
  private final JsonMapper jsonMapper;

  public String encode(NotificationTarget target) {
    return target == null ? null : jsonMapper.writeValueAsString(target);
  }

  public NotificationTarget decode(NotificationType type, String payload) {
    if (payload == null || payload.isBlank()) return null;
    return switch (type) {
      case SERIES_SUBSCRIBE -> jsonMapper.readValue(payload, NotificationSeriesRef.class);
      case CONNECTED, PATH_GREW -> jsonMapper.readValue(payload, NotificationCollectionRef.class);
      case LIKE, COMMENT, FOLLOW, REPLY, NEW_POST, MENTION ->
          jsonMapper.readValue(payload, NotificationPostRef.class);
    };
  }
}
