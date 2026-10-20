package com.example.short_link.room.domain;

import java.time.Instant;

public record RoomInvitee(RoomPerson person, Long inviterId, Instant invitedAt) {}
