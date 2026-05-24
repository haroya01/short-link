package com.example.short_link.user.api.response;

import java.time.Instant;

public record TwoFactorStatusResponse(boolean enabled, Instant lastUsedAt) {}
