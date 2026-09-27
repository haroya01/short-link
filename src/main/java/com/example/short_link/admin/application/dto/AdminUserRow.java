package com.example.short_link.admin.application.dto;

import java.time.Instant;

public record AdminUserRow(
    Long id,
    String email,
    String username,
    String role,
    boolean deleted,
    Instant createdAt,
    long linkCount) {}
