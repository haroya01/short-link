package com.example.short_link.user.presentation.response;

import java.time.Instant;

public record TwoFactorStatusResponse(boolean enabled, Instant lastUsedAt) {}
