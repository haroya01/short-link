package com.example.short_link.room.presentation.request;

import java.util.List;

public record InviteToRoomRequest(List<Long> userIds) {}
