package com.example.short_link.notification.application.dto;

import com.example.short_link.notification.domain.LinkNotificationType;
import java.time.Instant;

public record LinkNotificationView(
    Long id,
    LinkNotificationType type,
    String shortCode,
    String subtitle,
    String body,
    boolean read,
    Instant createdAt) {}
