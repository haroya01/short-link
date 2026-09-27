package com.example.short_link.notification.application.dto;

import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.NotificationType;
import java.time.Instant;

public record NotificationView(
    Long id,
    NotificationType type,
    NotificationActor actor,
    NotificationPostRef post,
    NotificationSeriesRef series,
    NotificationCollectionRef collection,
    boolean read,
    Instant createdAt) {}
