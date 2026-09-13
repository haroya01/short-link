package com.example.short_link.notification.domain;

/**
 * Deleted actors may be null or have null fields; the UI keeps the notification with an anonymous
 * label.
 */
public record NotificationActor(Long userId, String username, String avatarUrl) {}
