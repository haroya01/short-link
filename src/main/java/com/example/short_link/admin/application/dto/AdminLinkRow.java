package com.example.short_link.admin.application.dto;

import java.time.Instant;

/** 익명 링크의 {@code ownerId}와 {@code ownerEmail}은 null이다. */
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
    String status) {}
