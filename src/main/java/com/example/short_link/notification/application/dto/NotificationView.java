package com.example.short_link.notification.application.dto;

import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.NotificationType;
import java.time.Instant;

/**
 * A notification ready for rendering: the stored row joined with its read-time-resolved actor and
 * its decoded post reference. {@code actor} / {@code post} are null when absent (deleted actor, or
 * a postless type like FOLLOW).
 */
public record NotificationView(
    Long id,
    NotificationType type,
    NotificationActor actor,
    NotificationPostRef post,
    boolean read,
    Instant createdAt) {}
