package com.example.short_link.notification.application.dto;

import java.util.List;

public record NotificationListResult(
    List<NotificationView> items, Long nextCursor, boolean hasMore) {}
