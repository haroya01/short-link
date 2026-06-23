package com.example.short_link.notification.presentation.request;

import com.example.short_link.notification.domain.LinkNotificationType;
import jakarta.validation.constraints.NotNull;

/** Toggle one link-notification type on/off for the current user. */
public record UpdateNotificationPreferenceRequest(
    @NotNull LinkNotificationType type, boolean enabled) {}
