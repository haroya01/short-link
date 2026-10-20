package com.example.short_link.room.application.read;

import com.example.short_link.room.domain.RoomPerson;
import com.example.short_link.room.domain.RoomSummary;
import java.time.Instant;
import java.util.List;

public record RoomListItem(
    Long id,
    String name,
    long memberCount,
    String role,
    boolean hiddenFromFeed,
    boolean pinned,
    long unreadCount,
    Instant lastActivityAt,
    RoomSummary.LastNote lastNote,
    List<RoomPerson> faces) {

  static RoomListItem of(RoomSummary summary, List<RoomPerson> faces) {
    return new RoomListItem(
        summary.id(),
        summary.name(),
        summary.memberCount(),
        RoomRole.of(summary.owner()),
        summary.hiddenFromFeed(),
        summary.pinned(),
        summary.unreadCount(),
        summary.lastActivityAt(),
        summary.lastNote(),
        faces);
  }
}
