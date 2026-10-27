package com.example.short_link.common.event;

public record NoteRevisedEvent(Long noteId, Actor author, String excerpt) {}
