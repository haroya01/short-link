package com.example.short_link.room.presentation.request;

public record CreateRoomInviteRequest(Integer maxUses, Long expiresInSeconds, Boolean approval) {}
