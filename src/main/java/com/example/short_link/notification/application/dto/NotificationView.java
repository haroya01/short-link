package com.example.short_link.notification.application.dto;

import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.NotificationType;
import java.time.Instant;

/**
 * A notification ready for rendering: the stored row joined with its read-time-resolved actor and
 * its decoded target reference. {@code actor} is null when the actor was deleted. At most one of
 * {@code post} / {@code series} is set, per the row's type ({@code post} for LIKE/COMMENT/REPLY/
 * NEW_POST, {@code series} for SERIES_SUBSCRIBE, neither for FOLLOW).
 */
public record NotificationView(
    Long id,
    NotificationType type,
    NotificationActor actor,
    NotificationPostRef post,
    NotificationSeriesRef series,
    boolean read,
    Instant createdAt) {}
