package com.example.short_link.note.domain;

import java.time.Instant;

public record LocalPoolRow(Long id, Long userId, String language, Instant createdAt) {}
