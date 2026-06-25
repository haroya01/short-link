package com.example.short_link.notification.presentation.response;

import com.example.short_link.notification.application.dto.LinkNotificationListResult;
import java.util.List;

/** 링크 알림 한 페이지 — nextCursor 를 다음 요청의 {@code ?before=} 로 그대로 쓴다. */
public record LinkNotificationsPage(
    List<LinkNotificationResponse> items, Long nextCursor, boolean hasMore) {

  public static LinkNotificationsPage from(LinkNotificationListResult result) {
    return new LinkNotificationsPage(
        result.items().stream().map(LinkNotificationResponse::from).toList(),
        result.nextCursor(),
        result.hasMore());
  }
}
