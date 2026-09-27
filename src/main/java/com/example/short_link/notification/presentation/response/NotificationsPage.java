package com.example.short_link.notification.presentation.response;

import com.example.short_link.notification.application.dto.NotificationListResult;
import java.util.List;

public record NotificationsPage(
    List<NotificationResponse> items, Long nextCursor, boolean hasMore) {

  public static NotificationsPage from(NotificationListResult result) {
    return new NotificationsPage(
        result.items().stream().map(NotificationResponse::from).toList(),
        result.nextCursor(),
        result.hasMore());
  }
}
