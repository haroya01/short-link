package com.example.short_link.link.application.dto;

import java.time.Instant;
import java.util.List;

public record MyLink(
    String shortCode,
    String originalUrl,
    Instant createdAt,
    Instant expiresAt,
    long clickCount,
    List<String> tags,
    List<Long> clicksLast7d) {}
