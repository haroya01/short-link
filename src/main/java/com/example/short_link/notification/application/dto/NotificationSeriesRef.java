package com.example.short_link.notification.application.dto;

/**
 * Title is a write-time snapshot; the series link combines {@code slug} with the owner-recipient's
 * username.
 */
public record NotificationSeriesRef(Long seriesId, String slug, String title)
    implements NotificationTarget {}
