package com.example.short_link.notification.presentation.request;

import com.example.short_link.notification.domain.NotificationType;
import jakarta.validation.constraints.NotNull;

/** Boxed {@code @NotNull Boolean} rejects an omitted value instead of silently muting the type. */
public record UpdateBlogNotificationPreferenceRequest(
    @NotNull NotificationType type, @NotNull Boolean enabled) {}
