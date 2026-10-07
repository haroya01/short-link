package com.example.short_link.common.event;

public record NoteBroadcastEvent(Long noteId, Long authorId, String excerpt) {}
