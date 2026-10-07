package com.example.short_link.user.domain;

import java.time.Instant;

public record PendingFollowRequest(
    Long userId, String username, String displayName, String avatarUrl, Instant requestedAt) {}
