package com.example.short_link.notification.domain;

/**
 * The display identity of whoever triggered a notification, resolved at read time from the user
 * module. A null instance (or null fields) means the actor was deleted since the interaction — the
 * UI falls back to an anonymous label rather than dropping the notification.
 */
public record NotificationActor(Long userId, String username, String avatarUrl) {}
