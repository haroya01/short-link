package com.example.short_link.link.presentation.response;

import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.List;

public record LinkDetailResponse(
    ShortCode shortCode,
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
