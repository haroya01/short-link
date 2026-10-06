package com.example.short_link.common.event;

public record NoteRepostedEvent(Long repostId, Long noteId, Long userId) {}
