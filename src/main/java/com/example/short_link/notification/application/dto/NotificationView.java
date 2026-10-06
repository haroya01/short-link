package com.example.short_link.notification.application.dto;

import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;

// One row of the list. For a group, id, actor and createdAt are its newest member's, count is its
// size and actors the newest few; read means every member is read.
public record NotificationView(
    Long id,
    NotificationType type,
    NotificationActor actor,
    NotificationPostRef post,
    NotificationSeriesRef series,
    NotificationCollectionRef collection,
    NotificationNoteRef note,
    long count,
    List<NotificationActor> actors,
    boolean read,
    Instant createdAt) {}
