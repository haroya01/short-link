package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.List;

public record MyLink(
    ShortCode shortCode,
    String originalUrl,
    Instant createdAt,
    Instant expiresAt,
    long clickCount,
    List<String> tags,
    List<Long> clicksLast7d) {}
