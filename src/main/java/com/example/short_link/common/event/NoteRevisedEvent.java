package com.example.short_link.common.event;

public record NoteRevisedEvent(
    Long noteId, Long authorUserId, Long authorRemoteId, String excerpt) {}
