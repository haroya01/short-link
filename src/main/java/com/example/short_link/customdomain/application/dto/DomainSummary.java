package com.example.short_link.customdomain.application.dto;

import java.time.Instant;

public record DomainSummary(
    Long id,
    String domain,
    String verificationToken,
    String verificationHost,
    boolean verified,
    Instant verifiedAt,
    Instant lastCheckedAt,
    Instant createdAt,
    Instant autoVerifyUntil) {}
