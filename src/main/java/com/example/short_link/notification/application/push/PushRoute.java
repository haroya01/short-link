package com.example.short_link.notification.application.push;

public record PushRoute(
    String actorUsername,
    String ownerUsername,
    String postSlug,
    String seriesSlug,
    Long collectionId) {}
