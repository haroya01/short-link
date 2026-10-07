package com.example.short_link.notification.presentation.request;

// A member (actorUserId) or an account on another server (actorRemoteId), never both.
public record NotificationSenderRequest(Long actorUserId, Long actorRemoteId) {}
