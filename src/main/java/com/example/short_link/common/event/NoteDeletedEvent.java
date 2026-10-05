package com.example.short_link.common.event;

import java.util.List;

public record NoteDeletedEvent(Long noteId, Long authorId, List<String> mediaKeys) {}
