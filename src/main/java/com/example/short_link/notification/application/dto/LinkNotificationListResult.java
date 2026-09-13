package com.example.short_link.notification.application.dto;

import java.util.List;

/** nextCursor는 다음 요청의 before 값이다. */
public record LinkNotificationListResult(
    List<LinkNotificationView> items, Long nextCursor, boolean hasMore) {}
