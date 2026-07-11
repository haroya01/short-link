package com.example.short_link.notification.presentation.request;

import com.example.short_link.notification.domain.NotificationType;
import jakarta.validation.constraints.NotNull;

/**
 * Toggle one blog-bell notification type on/off for the current user. {@code enabled} is a boxed
 * {@code @NotNull Boolean} so an omitted field is rejected (400) rather than silently defaulting to
 * false and muting the type.
 */
public record UpdateBlogNotificationPreferenceRequest(
    @NotNull NotificationType type, @NotNull Boolean enabled) {}
