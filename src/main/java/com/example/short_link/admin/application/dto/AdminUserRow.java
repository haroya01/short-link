package com.example.short_link.admin.application.dto;

import java.time.Instant;

/**
 * One row of the admin user-browse table. {@code deleted} folds the {@code deletedAt} soft-delete
 * marker into a boolean; {@code linkCount} is the number of links the user owns (anonymous links
 * are not attributed to anyone and so aren't counted).
 */
public record AdminUserRow(
    Long id,
    String email,
    String username,
    String role,
    String tier,
    boolean deleted,
    Instant createdAt,
    long linkCount) {}
