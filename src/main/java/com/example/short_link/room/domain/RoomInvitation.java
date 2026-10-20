package com.example.short_link.room.domain;

import java.time.Instant;

public record RoomInvitation(
    Long roomId,
    String roomName,
    long memberCount,
    RoomPerson inviter,
    boolean approvalRequired,
    Instant invitedAt) {}
