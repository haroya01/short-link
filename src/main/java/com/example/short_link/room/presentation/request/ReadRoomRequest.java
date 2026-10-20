package com.example.short_link.room.presentation.request;

import jakarta.validation.constraints.NotNull;

public record ReadRoomRequest(@NotNull Long noteId) {}
