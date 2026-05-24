package com.example.short_link.link.presentation.response;

import java.time.Instant;
import java.util.List;

public record MyLinkResponse(
    String shortCode,
    String shortUrl,
    String originalUrl,
    Instant createdAt,
    Instant expiresAt,
    long clickCount,
    List<String> tags,
    List<Long> clicksLast7d) {}
