package com.example.short_link.room.domain;

import java.time.Instant;

public record RoomJoinRequest(RoomPerson person, Instant requestedAt) {}
