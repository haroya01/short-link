package com.example.short_link.room.application.read;

import com.example.short_link.room.domain.RoomSummary;
import java.time.Instant;

public record RoomListItem(
    Long id,
    String name,
    long memberCount,
    String role,
    boolean hiddenFromFeed,
    long unreadCount,
    Instant lastActivityAt,
    RoomSummary.LastNote lastNote) {

  static RoomListItem of(RoomSummary summary) {
    return new RoomListItem(
        summary.id(),
        summary.name(),
        summary.memberCount(),
        RoomRole.of(summary.owner()),
        summary.hiddenFromFeed(),
        summary.unreadCount(),
        summary.lastActivityAt(),
        summary.lastNote());
  }
}
