package com.example.short_link.notification.application.dto;

import java.util.List;

/** 링크 알림 한 페이지 — 커서(nextCursor = 다음 요청의 before)와 더 있는지(hasMore). */
public record LinkNotificationListResult(
    List<LinkNotificationView> items, Long nextCursor, boolean hasMore) {}
