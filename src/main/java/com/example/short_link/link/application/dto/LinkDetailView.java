package com.example.short_link.link.application.dto;

import java.time.Instant;
import java.util.List;

public record LinkDetailView(
    String shortCode,
    String originalUrl,
    Instant expiresAt,
    String ogTitle,
    String ogDescription,
    String ogImage,
    String ogTitleOverride,
    String ogDescriptionOverride,
    String ogImageOverride,
    boolean passwordProtected,
    Integer maxViews,
    int viewCount,
    boolean statsPublic,
    List<String> tags,
    String note,
    String expiredMessage) {}
