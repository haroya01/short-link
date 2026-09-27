package com.example.short_link.notification.application.dto;

public record NotificationSeriesRef(Long seriesId, String slug, String title)
    implements NotificationTarget {}
