package com.example.short_link.notification.application.dto;

/**
 * The series a SERIES_SUBSCRIBE notification points at, snapshotted into the row's JSON payload at
 * write time. {@code slug} (with the owner-recipient's own username) builds the series link; {@code
 * title} is the point-in-time display name.
 */
public record NotificationSeriesRef(Long seriesId, String slug, String title) {}
