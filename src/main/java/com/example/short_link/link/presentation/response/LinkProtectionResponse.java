package com.example.short_link.link.presentation.response;

public record LinkProtectionResponse(
    String shortCode, boolean passwordProtected, Integer maxViews, int viewCount) {}
