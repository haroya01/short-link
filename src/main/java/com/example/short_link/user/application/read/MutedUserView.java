package com.example.short_link.user.application.read;

import java.time.Instant;

public record MutedUserView(
    Long id, String username, String avatarUrl, boolean notifications, Instant expiresAt) {}
