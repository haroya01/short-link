package com.example.short_link.common.event;

public record NoteUnrepostedEvent(Long repostId, Long noteId, Long userId) {}
