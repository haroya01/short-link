package com.example.short_link.note.application.read;

import java.time.Instant;
import java.util.List;

public record PooledNote(
    long id,
    long userId,
    String language,
    Instant createdAt,
    List<String> tags,
    long likes,
    long reposts,
    long replies) {}
