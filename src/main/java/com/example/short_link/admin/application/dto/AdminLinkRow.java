package com.example.short_link.admin.application.dto;

import java.time.Instant;

public record AdminLinkRow(
    String shortCode,
    String originalUrl,
    Long ownerId,
    String ownerEmail,
    long clickCount,
    boolean passwordProtected,
    Integer maxViews,
    int viewCount,
    Instant createdAt,
    Instant expiresAt,
    String status,
    String disabledReason,
    Instant disabledAt) {}
