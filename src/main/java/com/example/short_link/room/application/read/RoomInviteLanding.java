package com.example.short_link.room.application.read;

import com.example.short_link.room.domain.RoomPerson;
import java.util.List;

public record RoomInviteLanding(
    String roomName,
    RoomPerson inviter,
    long memberCount,
    List<RoomPerson> members,
    boolean approval,
    boolean usable,
    String reason) {}
