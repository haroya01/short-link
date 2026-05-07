package com.example.short_link.user.api;

import java.time.Instant;

public record MeResponse(
    Long id, String email, String provider, String role, String timezone, Instant createdAt) {}
