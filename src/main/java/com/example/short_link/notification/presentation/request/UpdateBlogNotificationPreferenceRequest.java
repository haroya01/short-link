package com.example.short_link.notification.presentation.request;

import com.example.short_link.notification.domain.NotificationType;
import jakarta.validation.constraints.NotNull;

/** Toggle one blog-bell notification type on/off for the current user. */
public record UpdateBlogNotificationPreferenceRequest(
    @NotNull NotificationType type, boolean enabled) {}
