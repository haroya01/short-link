package com.example.short_link.notification.application.dto;

import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.NotificationType;
import java.time.Instant;

/**
 * {@code actor} is null after deletion. At most one of {@code post}, {@code series}, or {@code
 * collection} is set, according to notification type; FOLLOW has none.
 */
public record NotificationView(
    Long id,
    NotificationType type,
    NotificationActor actor,
    NotificationPostRef post,
    NotificationSeriesRef series,
    NotificationCollectionRef collection,
    boolean read,
    Instant createdAt) {}
