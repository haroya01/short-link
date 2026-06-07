package com.example.short_link.notification.application.dto;

import java.util.List;

/**
 * One newest-first page of notifications. {@code nextCursor} is the id to pass as {@code before}
 * for the following page, or null when {@code hasMore} is false.
 */
public record NotificationListResult(
    List<NotificationView> items, Long nextCursor, boolean hasMore) {}
