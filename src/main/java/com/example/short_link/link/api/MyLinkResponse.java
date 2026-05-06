package com.example.short_link.link.api;

import java.time.Instant;

public record MyLinkResponse(
    String shortCode, String shortUrl, String originalUrl, Instant createdAt, Instant expiresAt) {}
