package com.example.short_link.profile.application.write;

public record SetBlockHighlightCommand(Long userId, Long blockId, boolean highlighted) {}
