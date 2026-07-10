package com.example.short_link.admin.application.dto;

import java.time.Instant;

/**
 * One row of the admin link-browse table. {@code ownerId}/{@code ownerEmail} are null for anonymous
 * links. {@code status} is derived server-side ({@code ACTIVE} / {@code EXPIRED} / {@code
 * LIMIT_REACHED}) so the client doesn't re-implement the redirect gate's expiry logic.
 */
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
