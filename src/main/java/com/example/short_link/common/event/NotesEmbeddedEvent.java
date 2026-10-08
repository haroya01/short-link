package com.example.short_link.common.event;

import java.util.Set;

public record NotesEmbeddedEvent(
    Long actorUserId, Long postId, String postSlug, String postTitle, Set<Long> noteIds) {}
