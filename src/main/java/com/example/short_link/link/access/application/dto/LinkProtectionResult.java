package com.example.short_link.link.access.application.dto;

import com.example.short_link.link.domain.ShortCode;

public record LinkProtectionResult(
    ShortCode shortCode, boolean passwordProtected, Integer maxViews, int viewCount) {}
