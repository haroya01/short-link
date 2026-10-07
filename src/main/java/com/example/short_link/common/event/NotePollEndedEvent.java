package com.example.short_link.common.event;

import java.util.List;

public record NotePollEndedEvent(
    Long noteId, Long authorId, String noteExcerpt, List<Long> voterIds) {}
