package com.example.short_link.notification.presentation.response;

import com.example.short_link.notification.application.dto.LinkNotificationListResult;
import java.util.List;

public record LinkNotificationsPage(
    List<LinkNotificationResponse> items, Long nextCursor, boolean hasMore) {

  public static LinkNotificationsPage from(LinkNotificationListResult result) {
    return new LinkNotificationsPage(
        result.items().stream().map(LinkNotificationResponse::from).toList(),
        result.nextCursor(),
        result.hasMore());
  }
}
