package com.example.short_link.room.domain;

import java.time.Instant;

public record RoomSummary(
    Long id,
    String name,
    boolean owner,
    long memberCount,
    boolean hiddenFromFeed,
    long unreadCount,
    Instant lastActivityAt,
    LastNote lastNote) {

  public record LastNote(Long id, String excerpt, RoomPerson author, Instant createdAt) {}
}
