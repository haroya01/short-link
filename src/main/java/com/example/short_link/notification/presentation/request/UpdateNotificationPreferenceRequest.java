package com.example.short_link.notification.presentation.request;

import com.example.short_link.notification.domain.LinkNotificationType;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceRequest(
    @NotNull LinkNotificationType type, boolean enabled) {}
