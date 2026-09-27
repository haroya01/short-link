package com.example.short_link.notification.application.dto;

import java.util.List;

public record LinkNotificationListResult(
    List<LinkNotificationView> items, Long nextCursor, boolean hasMore) {}
